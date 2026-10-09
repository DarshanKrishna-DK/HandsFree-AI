import pytest
from pydantic import ValidationError

from backend.contracts import ActionProposal, NextActionRequest, validate_proposal


def request():
    return NextActionRequest.model_validate({
        "task_id": "test", "goal": "Calculate 250 + 150", "observation_id": "obs-1",
        "screen": {"package_name": "com.oneplus.calculator", "elements": [
            {"id": "e1", "text": "2", "description": "", "role": "Button",
             "actions": ["click"], "bounds": [0, 0, 100, 100], "editable": False}
        ]}, "allowed_actions": ["click", "finish_task", "ask_user"],
        "available_apps": [], "history": [], "constraints": [], "task_state": "PLANNING"
    })


def proposal(**changes):
    data = {"action": "click", "target_id": "e1", "arguments": {},
            "reason": "Enter the first digit", "observation_id": "obs-1",
            "requires_confirmation": False}
    return ActionProposal.model_validate(data | changes)


def test_valid_node_action_is_accepted():
    assert validate_proposal(proposal(), request()).action == "click"


@pytest.mark.parametrize("changes", [
    {"observation_id": "stale"}, {"target_id": "missing"},
    {"action": "type_text", "arguments": {"text": "hello"}},
    {"arguments": {"package_name": "com.android.settings"}},
    {"action": "finish_task", "target_id": None, "arguments": {"message": "Done"}},
])
def test_unsafe_or_unverifiable_proposals_are_rejected(changes):
    with pytest.raises(ValueError):
        validate_proposal(proposal(**changes), request())


def test_unknown_action_is_rejected():
    with pytest.raises(ValidationError):
        proposal(action="execute_shell", arguments={})


def test_extra_parameters_are_rejected():
    with pytest.raises(ValidationError):
        proposal(arguments={"shell": "reboot"})


def test_screen_payload_is_bounded():
    payload = request().model_dump()
    payload["screen"]["elements"] *= 301
    with pytest.raises(ValidationError):
        NextActionRequest.model_validate(payload)


def test_recommendation_without_observed_evidence_is_rejected():
    payload = request().model_dump()
    payload["allowed_actions"].append("speak")
    with pytest.raises(ValueError):
        validate_proposal(proposal(action="speak", target_id=None,
            arguments={"message": "This restaurant has a 4.8 rating"}), NextActionRequest.model_validate(payload))


def test_cart_fields_must_reference_current_evidence():
    with pytest.raises(ValueError, match="Cart details"):
        validate_proposal(proposal(action="finish_task", target_id=None,
            arguments={"message": "Cart", "expected_text": "2", "evidence_ids": ["e1"],
                       "cart_item_ids": ["invented"], "cart_total_ids": ["e1"]}), request())


def test_cart_fields_are_not_allowed_on_click():
    with pytest.raises(ValueError, match="Unsupported"):
        validate_proposal(proposal(arguments={"cart_item_ids": ["e1"]}), request())
