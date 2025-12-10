
from flask import Flask, request, jsonify
from paddleocr import PaddleOCR
import numpy as np
import cv2
import time

# Initialize the Flask app
app = Flask(__name__)

# --- OCR Engine Initialization ---
print("Initializing PaddleOCR...")
try:
    # use_textline_orientation=True is the replacement for the deprecated use_angle_cls.
    ocr = PaddleOCR(use_textline_orientation=True, lang="ch")
    print("PaddleOCR initialized successfully.")
except Exception as e:
    print(f"Error initializing PaddleOCR: {e}")
    ocr = None

# --- Helper function to format OCR result ---
def format_result(result):
    recognized_texts = []
    if result and result[0]:
        for line in result[0]:
            if line and len(line) >= 2 and isinstance(line[1], (list, tuple)) and len(line[1]) > 0:
                recognized_texts.append(line[1][0])
    return "\n".join(recognized_texts)

# --- API Endpoint for Mode 2 (Full Remote Processing) ---
@app.route('/ocr/full', methods=['POST'])
def ocr_full_pipeline():
    print("\n[Full] Received new request.")
    if ocr is None:
        print("[Full] Error: OCR engine not initialized.")
        return jsonify({'error': 'OCR engine not initialized'}), 500
        
    if 'file' not in request.files:
        print("[Full] Error: No file part in the request.")
        return jsonify({'error': 'No file part in the request'}), 400
    
    file_stream = request.files['file']
    
    print("[Full] Starting image preprocessing...")
    preproc_start_time = time.time()
    try:
        image_data = np.frombuffer(file_stream.read(), np.uint8)
        img = cv2.imdecode(image_data, cv2.IMREAD_COLOR)
        if img is None:
            print("[Full] Error: Could not decode image.")
            return jsonify({'error': 'Could not decode image'}), 400
    except Exception as e:
        print(f"[Full] Error: Image decoding failed: {e}")
        return jsonify({'error': f'Image decoding failed: {e}'}), 400
    preproc_end_time = time.time()
    preprocessing_time_ms = (preproc_end_time - preproc_start_time) * 1000
    print(f"[Full] Preprocessing complete in {preprocessing_time_ms:.2f}ms.")

    print("[Full] Starting OCR inference...")
    infer_start_time = time.time()
    # FIX: Removed the unsupported 'cls=True' argument.
    result = ocr.ocr(img)
    infer_end_time = time.time()
    inference_time_ms = (infer_end_time - infer_start_time) * 1000
    print(f"[Full] Inference complete in {inference_time_ms:.2f}ms.")

    final_text = format_result(result)
    total_server_time_ms = preprocessing_time_ms + inference_time_ms
    print(f"[Full] Sending response.")

    return jsonify({
        'recognized_text': final_text,
        'preprocessing_time_ms': round(preprocessing_time_ms),
        'inference_time_ms': round(inference_time_ms),
        'total_server_time_ms': round(total_server_time_ms)
    })

# --- API Endpoint for Mode 3 (Remote Inference Only) ---
@app.route('/ocr/infer_only', methods=['POST'])
def ocr_inference_only():
    print("\n[Infer Only] Received new request.")
    if ocr is None:
        print("[Infer Only] Error: OCR engine not initialized.")
        return jsonify({'error': 'OCR engine not initialized'}), 500

    if 'file' not in request.files:
        print("[Infer Only] Error: No file part in the request.")
        return jsonify({'error': 'No file part in the request'}), 400
    
    file_stream = request.files['file']
    
    print("[Infer Only] Decoding received image...")
    try:
        image_data = np.frombuffer(file_stream.read(), np.uint8)
        img = cv2.imdecode(image_data, cv2.IMREAD_COLOR)
        if img is None:
            print("[Infer Only] Error: Could not decode image.")
            return jsonify({'error': 'Could not decode image'}), 400
    except Exception as e:
        print(f"[Infer Only] Error: Image decoding failed: {e}")
        return jsonify({'error': f'Image decoding failed: {e}'}), 400
    print("[Infer Only] Image decoding complete.")

    print("[Infer Only] Starting OCR inference...")
    infer_start_time = time.time()
    # FIX: Removed the unsupported 'cls=True' argument.
    result = ocr.ocr(img)
    infer_end_time = time.time()
    inference_time_ms = (infer_end_time - infer_start_time) * 1000
    print(f"[Infer Only] Inference complete in {inference_time_ms:.2f}ms.")

    final_text = format_result(result)
    print(f"[Infer Only] Sending response.")

    return jsonify({
        'recognized_text': final_text,
        'inference_time_ms': round(inference_time_ms)
    })

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)
