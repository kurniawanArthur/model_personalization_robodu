import tensorflow as tf
from tensorflow.python.framework.convert_to_constants import convert_variables_to_constants_v2
import os

saved_model_dir = "saved_model"   # your saved_model folder
tflite_file = "model_frozen.tflite"

# 1) Load the SavedModel and get a callable concrete function for inference.
model = tf.saved_model.load(saved_model_dir)
# try to get the 'serving_default' signature if present
if "serving_default" in model.signatures:
    concrete_func = model.signatures["serving_default"]
else:
    # fallback: try to fetch any available function (the user script used 'infer' signature)
    try:
        concrete_func = model.signatures["infer"]
    except Exception:
        # As a last resort, pick the first function (may need adjustment)
        concrete_func = list(model.signatures.values())[0]

# 2) Convert variables to constants (freeze)
frozen_func = convert_variables_to_constants_v2(concrete_func)
frozen_graph_def = frozen_func.graph.as_graph_def()

print("Frozen graph inputs:", frozen_func.inputs)
print("Frozen graph outputs:", frozen_func.outputs)

converter = tf.lite.TFLiteConverter.from_concrete_functions([frozen_func])
tflite_model = converter.convert()

with open(tflite_file, "wb") as f:
    f.write(tflite_model)

print("Saved TFLite to", tflite_file)
