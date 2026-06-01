import sys
import os
import json
from dotenv import load_dotenv
from huggingface_hub import InferenceClient

env_path = os.path.join(os.path.dirname(__file__), '.env')
load_dotenv(dotenv_path=env_path)

def get_ai_recommendation(data):
    api_key = os.environ.get("HF_API_KEY")

    if not api_key:
        return _fallback_recommendation(data)

    try:
        client = InferenceClient(api_key=api_key)

        messages = [
            {
                "role": "system",
                "content": (
                    "You are DeepWorkAI, a productivity coach. "
                    "Based on the user's distraction data, give one short, practical tip "
                    "to reduce distractions during focus sessions. Keep it under 2 sentences."
                )
            },
            {
                "role": "user",
                "content": f"My distraction data from recent focus sessions: {data}"
            }
        ]

        response = client.chat.completions.create(
            model="Qwen/Qwen2.5-72B-Instruct",
            messages=messages,
            max_tokens=80,
            temperature=0.7
        )

        result = response.choices[0].message.content.strip()
        return result if result else _fallback_recommendation(data)

    except Exception as e:
        sys.stderr.write(f"get_ai_recommendation error: {e}\n")
        return _fallback_recommendation(data)


def _fallback_recommendation(data):
    try:
        sessions = json.loads(data)
        if sessions and isinstance(sessions, list) and "apps" in sessions[0] and len(sessions[0]["apps"]) > 0:
            top_app = sessions[0]["apps"][0]["appName"]
        else:
            top_app = "distracting apps"
        return (
            f"Consider blocking {top_app} during focus sessions. "
            "Research shows it takes around 23 minutes to fully regain concentration after an interruption."
        )
    except Exception:
        return "Limit your usage of distracting apps during focus sessions to maintain deep work quality."


if __name__ == "__main__":
    if len(sys.argv) > 1:
        data_arg = sys.argv[1]
        print(get_ai_recommendation(data_arg))
    else:
        print("No data provided.")
