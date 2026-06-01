import sys
import os
import joblib
import numpy as np
import pandas as pd

def predict():
    # Read arguments passed from Ktor
    duration = float(sys.argv[1])
    hour = int(sys.argv[2])
    distractions = int(sys.argv[3])
    score = int(sys.argv[4])

    # Compute distraction_rate — same feature used during training
    distraction_rate = distractions / (duration + 1)

    model_path = os.path.join(os.path.dirname(__file__), "burnout_model.pkl")

    try:
        model = joblib.load(model_path)
        features = pd.DataFrame([[duration, hour, distraction_rate, score]],
                                columns=["duration_min", "hour_of_day", "distraction_rate", "focus_score"])
        prediction = model.predict(features)[0]
        print(int(prediction))
    except Exception as e:
        # Fallback to rule-based if model loading fails
        sys.stderr.write(f"Model load failed: {e}\n")
        risk = 0
        if duration > 120 and score < 60:
            risk = 2
        elif duration > 60 and distraction_rate > 0.5:
            risk = 1
        elif score < 50 and duration > 30:
            risk = 2
        print(risk)

if __name__ == "__main__":
    predict()
