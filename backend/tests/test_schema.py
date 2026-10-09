from backend.model_schema import action_schema
from backend.model_schema import grounded_schema
from backend.model_schema import relevant_context
from backend.model_schema import planning_payload
from backend.contracts import NextActionRequest


def test_planner_preserves_kannada_response_language_and_original_labels():
    request = NextActionRequest(task_id="kn", goal="ಆಹಾರ ಬೇಕು", observation_id="s", response_language="kn-IN",
        screen={"package_name": "food.app", "elements": []}, allowed_actions=["ask_user"])
    assert planning_payload(request).get("response_language") == "kn-IN"
    assert planning_payload(request)["goal"] == "ಆಹಾರ ಬೇಕು"


def test_open_app_schema_requires_non_null_package():
    variant = action_schema(["open_app"])["anyOf"][0]
    assert "arguments" in variant["required"]
    args = variant["properties"]["arguments"]
    assert args["required"] == ["package_name"]
    assert args["properties"]["package_name"]["type"] == "string"
    assert args["additionalProperties"] is False


def test_click_schema_requires_target_and_empty_arguments():
    variant = action_schema(["click"])["anyOf"][0]
    assert "target_id" in variant["required"]
    assert variant["properties"]["target_id"]["type"] == "string"
    assert variant["properties"]["arguments"]["properties"] == {}


def test_grammar_binds_current_observation_and_real_targets():
    request = NextActionRequest(task_id="test", goal="Open Calculator", observation_id="s-current",
        screen={"package_name": "com.example.handsfree", "elements": []},
        available_apps=[{"name": "Calculator", "package_name": "com.oneplus.calculator"}],
        allowed_actions=["open_app", "click", "finish_task", "ask_user"])
    variants = grounded_schema(request)["anyOf"]
    assert len(variants) == 2
    assert all(v["properties"]["observation_id"]["const"] == "s-current" for v in variants)
    assert variants[0]["properties"]["arguments"]["properties"]["package_name"]["enum"] == ["com.oneplus.calculator"]


def test_app_retrieval_preserves_ambiguous_actual_matches():
    request = NextActionRequest(task_id="test", goal="Open Calculator and calculate 250 + 150", observation_id="s",
        screen={"package_name": "com.example.handsfree", "elements": []}, allowed_actions=["open_app", "ask_user"],
        available_apps=[{"name": "Assistant", "package_name": "com.example.assistant"},
                        {"name": "Calculator", "package_name": "com.oneplus.calculator"},
                        {"name": "Calculator", "package_name": "com.google.android.calculator"}])
    assert [a.name for a in relevant_context(request).available_apps] == ["Calculator", "Calculator"]


def test_compaction_preserves_observed_labels_and_capabilities():
    request = NextActionRequest(task_id="test", goal="Read", observation_id="s",
        screen={"package_name": "com.example.app", "elements": [{"id": "e1", "text": "250+150",
        "description": "Expression", "role": "android.widget.EditText", "editable": True,
        "actions": ["type_text"], "bounds": [0, 0, 100, 50]}]}, allowed_actions=["read_screen"])
    element = planning_payload(request)["screen"]["elements"][0]
    assert element == {"id": "e1", "text": "250+150", "description": "Expression", "role": "EditText", "editable": True, "actions": ["type_text"]}


def test_calculator_uses_real_keys_instead_of_display_replacement():
    request = NextActionRequest(task_id="test", goal="Calculate 250 + 150", observation_id="s",
        screen={"package_name": "com.oneplus.calculator", "elements": [{"id": "e1", "text": "0",
        "editable": True, "actions": ["type_text"], "bounds": [0, 0, 1, 1]}]},
        allowed_actions=["type_text", "ask_user"])
    assert [v["properties"]["action"]["const"] for v in grounded_schema(request)["anyOf"]] == ["ask_user"]


def test_cart_grammar_uses_real_ids_and_keeps_nonfood_arrays_optional_in_content():
    request = NextActionRequest(task_id="test", goal="Review food cart", observation_id="s",
        screen={"package_name": "com.application.zomato", "elements": [{"id": "e1", "text": "Grand total ₹320",
        "actions": [], "bounds": [0, 0, 100, 50]}]}, allowed_actions=["finish_task"])
    fields = grounded_schema(request)["anyOf"][0]["properties"]["arguments"]["properties"]
    assert fields["cart_item_ids"]["items"]["enum"] == ["e1"]
    assert fields["cart_total_ids"]["items"]["enum"] == ["e1"]
    assert fields["cart_item_ids"].get("minItems", 0) == 0
