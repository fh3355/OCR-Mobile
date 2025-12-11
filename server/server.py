from flask import Flask, request, jsonify
from paddleocr import PaddleOCR
import numpy as np
import cv2
import time

app = Flask(__name__)

# --- OCR Engine Initialization ---
# Initialize both models at startup and store them in a dictionary.
ocr_engines = {}
try:
    print("Initializing default PP-OCR Server Model (v5-like)...")
    # The default model is the latest server version, providing high accuracy.
    ocr_engines["v5"] = PaddleOCR(use_angle_cls=True, lang="ch")
    print("Default PP-OCR Server Model initialized.")

    print("Initializing PP-OCR Mobile Model (v2.0)...")
    # Explicitly load the v2 mobile model for consistency with the app's local mode.
    ocr_engines["v2"] = PaddleOCR(ocr_version="PP-OCRv2", use_angle_cls=True, lang="ch")
    print("PP-OCR v2 Mobile Model initialized.")

except Exception as e:
    print(f"Fatal error during OCR engine initialization: {e}")

# --- Helper function to format OCR result ---
def format_result(result):
    recognized_texts = []
    if result and result[0]:
        for line in result[0]:
            if line and len(line) >= 2 and isinstance(line[1], (list, tuple)) and len(line[1]) > 0:
                recognized_texts.append(line[1][0])
    return "\n".join(recognized_texts)

def get_ocr_engine():
    # Default to the high-accuracy v5 model if not specified
    model_version = request.form.get('model_version', 'v5') 
    if model_version not in ocr_engines:
        print(f"Warning: Requested model '{model_version}' not found. Falling back to 'v5'.")
        return ocr_engines.get("v5"), "v5"
    return ocr_engines.get(model_version), model_version

# --- API Endpoints ---
@app.route('/ocr/full', methods=['POST'])
def ocr_full_pipeline():
    ocr, model_ver = get_ocr_engine()
    print(f"\n[Full | {model_ver}] Received new request.")
    
    if ocr is None:
        return jsonify({'error': 'Selected OCR engine is not available on the server'}), 500
    if 'file' not in request.files:
        return jsonify({'error': 'No file part in the request'}), 400
    
    file_stream = request.files['file']
    
    print(f"[Full | {model_ver}] Starting image preprocessing...")
    preproc_start_time = time.time()
    try:
        image_data = np.frombuffer(file_stream.read(), np.uint8)
        img = cv2.imdecode(image_data, cv2.IMREAD_COLOR)
        if img is None: return jsonify({'error': 'Could not decode image'}), 400
    except Exception as e: return jsonify({'error': f'Image decoding failed: {e}'}), 400
    preproc_end_time = time.time()
    preprocessing_time_ms = (preproc_end_time - preproc_start_time) * 1000
    print(f"[Full | {model_ver}] Preprocessing complete in {preprocessing_time_ms:.2f}ms.")

    print(f"[Full | {model_ver}] Starting OCR inference...")
    infer_start_time = time.time()
    result = ocr.ocr(img, cls=True)
    infer_end_time = time.time()
    inference_time_ms = (infer_end_time - infer_start_time) * 1000
    print(f"[Full | {model_ver}] Inference complete in {inference_time_ms:.2f}ms.")

    final_text = format_result(result)
    total_server_time_ms = preprocessing_time_ms + inference_time_ms
    print(f"[Full | {model_ver}] Sending response.")

    return jsonify({
        'recognized_text': final_text,
        'preprocessing_time_ms': round(preprocessing_time_ms),
        'inference_time_ms': round(inference_time_ms),
        'total_server_time_ms': round(total_server_time_ms)
    })

@app.route('/ocr/infer_only', methods=['POST'])
def ocr_inference_only():
    ocr, model_ver = get_ocr_engine()
    print(f"\n[Infer Only | {model_ver}] Received new request.")

    if ocr is None: return jsonify({'error': 'Selected OCR engine is not available on the server'}), 500
    if 'file' not in request.files: return jsonify({'error': 'No file part in the request'}), 400
    
    file_stream = request.files['file']
    
    try:
        image_data = np.frombuffer(file_stream.read(), np.uint8)
        img = cv2.imdecode(image_data, cv2.IMREAD_COLOR)
        if img is None: return jsonify({'error': 'Could not decode image'}), 400
    except Exception as e: return jsonify({'error': f'Image decoding failed: {e}'}), 400

    print(f"[Infer Only | {model_ver}] Starting OCR inference...")
    infer_start_time = time.time()
    result = ocr.ocr(img, cls=True)
    infer_end_time = time.time()
    inference_time_ms = (infer_end_time - infer_start_time) * 1000
    print(f"[Infer Only | {model_ver}] Inference complete in {inference_time_ms:.2f}ms.")

    final_text = format_result(result)
    print(f"[Infer Only | {model_ver}] Sending response.")

    return jsonify({
        'recognized_text': final_text,
        'inference_time_ms': round(inference_time_ms)
    })

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)
