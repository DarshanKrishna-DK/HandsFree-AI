"""Action-specific grammar prevents structurally incomplete model proposals."""
from copy import deepcopy
import re
from .contracts import ActionProposal, Arguments, PARAMETERS, NextActionRequest


def relevant_context(request: NextActionRequest) -> NextActionRequest:
    """Retrieve explicitly named installed apps, preserving all ambiguous matches."""
    user_text = " ".join([request.goal, *request.constraints]).casefold()
    matches = [app for app in request.available_apps if len(app.name.strip()) >= 3 and
               re.search(r"(?<!\w)" + re.escape(app.name.strip().casefold()) + r"(?!\w)", user_text)]
    if not matches:
        return request
    return request.model_copy(update={"available_apps": matches})


def planning_payload(request: NextActionRequest) -> dict:
    """Keep labels/capabilities; pixel coordinates are unnecessary for node actions."""
    elements = []
    for element in request.screen.elements:
        compact = {"id": element.id, "role": element.role.rsplit(".", 1)[-1], "actions": element.actions}
        if element.text:
            compact["text"] = element.text
        if element.description and element.description != element.text:
            compact["description"] = element.description
        if element.editable:
            compact["editable"] = True
        elements.append(compact)
    return {"goal": request.goal, "constraints": request.constraints, "response_language": request.response_language,
            "available_apps": [a.model_dump() for a in request.available_apps],
            "allowed_actions": request.allowed_actions, "task_state": request.task_state,
            "history": [h.model_dump() for h in request.history],
            "previous_action_result": request.previous_action_result,
            "observation_id": request.observation_id,
            "screen": {"package_name": request.screen.package_name,
                       "blocked_reason": request.screen.blocked_reason, "elements": elements}}


def action_schema(allowed_actions: list[str]) -> dict:
    common = ActionProposal.model_json_schema()["properties"]
    arguments = Arguments.model_json_schema()["properties"]
    variants = []
    for action in allowed_actions:
        props = deepcopy(common)
        props["action"] = {"const": action, "type": "string"}
        props["target_id"] = ({"type": "string", "minLength": 1, "maxLength": 80}
                              if action in {"click", "type_text", "scroll"} else {"type": "null"})
        fields = sorted(PARAMETERS[action])
        arg_props = {}
        for field in fields:
            spec = deepcopy(arguments[field])
            if "anyOf" in spec:
                spec = next(s for s in spec["anyOf"] if s.get("type") != "null")
            spec.pop("default", None)
            if field == "evidence_ids":
                spec["minItems"] = 1
            arg_props[field] = spec
        props["arguments"] = {"type": "object", "properties": arg_props,
                              "required": fields, "additionalProperties": False}
        variants.append({"type": "object", "properties": props,
                         "required": list(props), "additionalProperties": False})
    return {"anyOf": variants}


def grounded_schema(request: NextActionRequest) -> dict:
    schema = action_schema(request.allowed_actions)
    variants = []
    for variant in schema["anyOf"]:
        props = variant["properties"]
        action = props["action"]["const"]
        # OnePlus accepts ACTION_SET_TEXT but does not update its calculator engine.
        # Use visible calculator keys so the app actually evaluates the expression.
        if action == "type_text" and request.screen.package_name in {
                "com.oneplus.calculator", "com.google.android.calculator", "com.android.calculator2"}:
            continue
        props["observation_id"] = {"type": "string", "const": request.observation_id}
        if action in {"click", "type_text", "scroll"}:
            targets = [e.id for e in request.screen.elements if action in e.actions]
            if not targets:
                continue
            props["target_id"]["enum"] = targets
        args = props["arguments"]["properties"]
        if action == "open_app":
            packages = [a.package_name for a in request.available_apps]
            if not packages:
                continue
            args["package_name"]["enum"] = packages
        if "evidence_ids" in args:
            if not request.screen.elements:
                continue
            args["evidence_ids"]["items"] = {"type": "string", "enum": [e.id for e in request.screen.elements]}
        for field in ("cart_item_ids", "cart_total_ids"):
            if field in args:
                args[field]["items"] = {"type": "string", "enum": [e.id for e in request.screen.elements]}
        variants.append(variant)
    return {"anyOf": variants}
