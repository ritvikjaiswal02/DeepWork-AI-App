import sys
import os
from dotenv import load_dotenv
from huggingface_hub import InferenceClient

env_path = os.path.join(os.path.dirname(__file__), '.env')
load_dotenv(dotenv_path=env_path)

def ask_ai(query, context, schedule):
    api_key = os.environ.get("HF_API_KEY") 
    
    if not api_key:
        return "API Key is missing. Please check your .env file."
        
    try:
        # Use HuggingFace InferenceClient
        client = InferenceClient(api_key=api_key)
        
        system_prompt = (
            "You are Cortex, the productivity assistant inside the DeepWorkAI app. "
            "You answer using the user's REAL data shown below. "
            "Rules: "
            "(1) Reference at least one specific number or fact from the USER DATA in every reply "
            "(focus score, session count, a distracting app name, etc.). "
            "(2) Be concrete and personal, never generic. "
            "(3) Keep the reply under 4 sentences. "
            "(4) If the data shows a clear issue (low score, frequent distractions, no sessions, no sleep), "
            "name it directly and give one actionable fix. "
            "(5) Do not invent numbers that are not in the data.\n\n"
            f"--- USER DATA ---\n{context}\n"
            f"--- USER'S DAILY SCHEDULE ---\n{schedule if schedule else 'not provided'}"
        )

        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": query}
        ]

        response = client.chat.completions.create(
            model="Qwen/Qwen2.5-72B-Instruct",
            messages=messages,
            max_tokens=220,
            temperature=0.6
        )
        
        return response.choices[0].message.content.strip()
    except Exception as e:
        if "403" in str(e) or "Forbidden" in str(e):
            return "Your HuggingFace API key does not have Inference permissions. Please create a new token and check 'Make calls to the Serverless Inference API'."
        return f"Failed to connect to the AI service. Details: {str(e)}"

if __name__ == "__main__":
    if len(sys.argv) > 3:
        query_arg = sys.argv[1]
        context_arg = sys.argv[2]
        schedule_arg = sys.argv[3]
        print(ask_ai(query_arg, context_arg, schedule_arg))
    else:
        print("Error: Missing arguments. Expected query, context, and schedule.")
