"""Bounded wire contracts. Model output is a proposal, never authorization."""
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

Action = Literal["open_app", "read_screen", "click", "type_text", "scroll", "go_back",
                 "ask_user", "open_play_store", "speak", "finish_task", "stop_task"]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class Element(StrictModel):
    id: str = Field(min_length=1, max_length=80)
    text: str = Field(default="", max_length=500)
    description: str = Field(default="", max_length=500)
    role: str = Field(default="", max_length=120)
    actions: list[Literal["click", "type_text", "scroll"]] = Field(default_factory=list, max_length=3)
    bounds: list[int] = Field(min_length=4, max_length=4)
    editable: bool = False


class Screen(StrictModel):
    package_name: str = Field(max_length=200)
    elements: list[Element] = Field(default_factory=list, max_length=300)
    blocked_reason: str | None = Field(default=None, max_length=300)


class App(StrictModel):
    package_name: str = Field(min_length=1, max_length=200)
    name: str = Field(min_length=1, max_length=200)


class HistoryEntry(StrictModel):
    action: str = Field(max_length=40)
    result: str = Field(max_length=500)


class NextActionRequest(StrictModel):
    response_language: Literal["en-IN", "kn-IN"] = "en-IN"
    task_id: str = Field(min_length=1, max_length=80)
    goal: str = Field(min_length=1, max_length=2000)
    observation_id: str = Field(min_length=1, max_length=80)
    task_state: str = Field(default="PLANNING", max_length=40)
    screen: Screen
    available_apps: list[App] = Field(default_factory=list, max_length=500)
    allowed_actions: list[Action] = Field(min_length=1, max_length=11)
    history: list[HistoryEntry] = Field(default_factory=list, max_length=40)
    constraints: list[str] = Field(default_factory=list, max_length=20)
    previous_action_result: str | None = Field(default=None, max_length=1000)


class Arguments(StrictModel):
    package_name: str | None = Field(default=None, max_length=200)
    text: str | None = Field(default=None, max_length=1000)
    direction: Literal["forward", "backward"] | None = None
    message: str | None = Field(default=None, max_length=1000)
    expected_text: str | None = Field(default=None, max_length=500)
    evidence_ids: list[str] = Field(default_factory=list, max_length=10)
    cart_item_ids: list[str] = Field(default_factory=list, max_length=10)
    cart_total_ids: list[str] = Field(default_factory=list, max_length=3)


class ActionProposal(StrictModel):
    action: Action
    target_id: str | None = Field(default=None, max_length=80)
    arguments: Arguments = Field(default_factory=Arguments)
    reason: str = Field(min_length=1, max_length=500)
    observation_id: str = Field(min_length=1, max_length=80)
    requires_confirmation: bool


PARAMETERS = {
    "open_app": {"package_name"}, "read_screen": set(), "click": set(),
    "type_text": {"text"}, "scroll": {"direction"}, "go_back": set(),
    "ask_user": {"message"}, "open_play_store": {"package_name"},
    "speak": {"message", "evidence_ids"},
    "finish_task": {"message", "expected_text", "evidence_ids", "cart_item_ids", "cart_total_ids"}, "stop_task": {"message"},
}


def validate_proposal(proposal: ActionProposal, request: NextActionRequest) -> ActionProposal:
    """Reject inconsistent proposals before Android independently revalidates them."""
    if proposal.action not in request.allowed_actions:
        raise ValueError("Action is not available for this observation")
    if proposal.observation_id != request.observation_id:
        raise ValueError("Stale observation identifier")
    if request.screen.blocked_reason and proposal.action not in {"ask_user", "stop_task"}:
        raise ValueError("Protected or unsupported screen")
    supplied = {k for k, v in proposal.arguments.model_dump().items() if v not in (None, [])}
    if supplied - PARAMETERS[proposal.action]:
        raise ValueError("Unsupported action parameters")
    elements = {e.id: e for e in request.screen.elements}
    if len(elements) != len(request.screen.elements):
        raise ValueError("Duplicate element identifiers")
    if proposal.action in {"click", "type_text", "scroll"}:
        element = elements.get(proposal.target_id)
        if element is None or proposal.action not in element.actions:
            raise ValueError("Missing target or unsupported node action")
        if proposal.action == "type_text" and (not element.editable or proposal.arguments.text is None):
            raise ValueError("Text requires an editable target and text argument")
        if proposal.action == "scroll" and proposal.arguments.direction is None:
            raise ValueError("Scroll direction is required")
    elif proposal.target_id is not None:
        raise ValueError("This action does not accept a target")
    if proposal.action in {"open_app", "open_play_store"}:
        package = proposal.arguments.package_name
        import re
        if not package or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+", package):
            raise ValueError("A valid package identifier is required")
        if proposal.action == "open_app" and package not in {a.package_name for a in request.available_apps}:
            raise ValueError("Application is not in the observed inventory")
        # Android still owns app choice and installation consent.
        proposal.requires_confirmation = True
    if proposal.action in {"ask_user", "speak", "finish_task"} and not proposal.arguments.message:
        raise ValueError("Message is required")
    if any(i not in elements for i in proposal.arguments.evidence_ids):
        raise ValueError("Evidence references an unobserved element")
    if any(i not in proposal.arguments.evidence_ids for i in
           proposal.arguments.cart_item_ids + proposal.arguments.cart_total_ids):
        raise ValueError("Cart details must reference current evidence")
    if proposal.action == "speak" and not proposal.arguments.evidence_ids:
        raise ValueError("Recommendations require observed evidence")
    if proposal.action == "finish_task":
        expected = proposal.arguments.expected_text
        evidence = proposal.arguments.evidence_ids
        if not expected or not evidence:
            raise ValueError("Completion requires visible evidence and expected text")
        if not any(expected.casefold() in (elements[i].text + " " + elements[i].description).casefold()
                   for i in evidence):
            raise ValueError("Completion evidence does not contain the expected result")
    return proposal
