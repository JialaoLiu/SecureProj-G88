export class WS {
  private ws?: WebSocket;
  private url: string;
  private timer?: number;
  private reconnectTimer?: number;
  private currentUser: string = 'frontend-dev';

  constructor(url: string) {
    this.url = url;
  }

  setUser(username: string) {
    this.currentUser = username;
  }

  private generateNonce(): string {
    // Generate a cryptographically secure 32-character random nonce
    const array = new Uint8Array(16);
    crypto.getRandomValues(array);
    return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('');
  }

  connect(onMsg: (m: any) => void) {
    this.ws = new WebSocket(this.url);

    this.ws.onopen = () => {
      console.log('WebSocket Connected');
      // 最小 USER_HELLO（sig 用占位，后续由 B 同学签名）
      const hello = {
        type: "USER_HELLO",
        from: this.currentUser,
        to: "server",
        ts: Math.floor(Date.now() / 1000),
        nonce: this.generateNonce(),
        payload: { client: this.currentUser, pubkey: "dev-pubkey", enc_pubkey: "dev-enc-pubkey" },
        sig: "dev-mock"
      };
      this.ws?.send(JSON.stringify(hello));
      this.heartbeat();
    };

    this.ws.onmessage = (e) => {
      try {
        onMsg(JSON.parse(e.data));
      } catch {
        /* ignore invalid JSON */
      }
    };

    this.ws.onclose = () => {
      console.log('WebSocket Disconnected');
      this.reconnect(onMsg);
    };
  }

  private heartbeat() {
    this.timer && clearInterval(this.timer);
    // 心跳 25s
    this.timer = setInterval(() => {
      const hb = {
        type: "HEARTBEAT",
        from: this.currentUser,
        to: "server",
        ts: Math.floor(Date.now() / 1000),
        nonce: this.generateNonce(),
        payload: {},
        sig: "dev-mock"
      };
      this.ws?.send(JSON.stringify(hb));
    }, 25000) as unknown as number;
  }

  private reconnect(onMsg: (m: any) => void) {
    this.reconnectTimer = setTimeout(() => this.connect(onMsg), 1000) as unknown as number;
  }

  send(obj: any) {
    // Add nonce if not already present
    if (!obj.nonce) {
      obj.nonce = this.generateNonce();
    }
    this.ws?.send(JSON.stringify(obj));
  }

  async sendFile(file: File, to: string) {
    const fileId = this.generateFileId();
    const chunkSize = 256 * 1024; // 256KB chunks
    const totalChunks = Math.ceil(file.size / chunkSize);

    // Calculate SHA256 hash
    const sha256 = await this.calculateSHA256(file);

    // Send FILE_START
    const fileStart = {
      type: "FILE_START",
      from: this.currentUser,
      to: to,
      ts: Math.floor(Date.now() / 1000),
      nonce: this.generateNonce(),
      payload: {
        file_id: fileId,
        name: file.name,
        size: file.size,
        sha256: sha256,
        mode: to === "*" ? "public" : "dm"
      },
      sig: "dev-mock"
    };

    this.send(fileStart);

    // Send chunks with delay to respect rate limiting
    for (let i = 0; i < totalChunks; i++) {
      const start = i * chunkSize;
      const end = Math.min(start + chunkSize, file.size);
      const chunk = file.slice(start, end);

      const arrayBuffer = await chunk.arrayBuffer();
      const uint8Array = new Uint8Array(arrayBuffer);

      // 安全的方式转换大数据，避免栈溢出
      let binaryString = '';
      for (let i = 0; i < uint8Array.length; i++) {
        binaryString += String.fromCharCode(uint8Array[i]);
      }
      const base64Data = btoa(binaryString);

      const fileChunk = {
        type: "FILE_CHUNK",
        from: this.currentUser,
        to: to,
        ts: Math.floor(Date.now() / 1000),
        nonce: this.generateNonce(),
        payload: {
          file_id: fileId,
          index: i,
          ciphertext: base64Data
        },
        sig: "dev-mock"
      };

      this.send(fileChunk);

      // Rate limiting: delay between chunks (200ms = 5 chunks/second)
      if (i < totalChunks - 1) {
        await new Promise(resolve => setTimeout(resolve, 200));
      }
    }

    // Send FILE_END (include file metadata for other users)
    const fileEnd = {
      type: "FILE_END",
      from: this.currentUser,
      to: to,
      ts: Math.floor(Date.now() / 1000),
      nonce: this.generateNonce(),
      payload: {
        file_id: fileId,
        name: file.name,
        size: file.size
      },
      sig: "dev-mock"
    };

    this.send(fileEnd);
    console.log(`[FileTransfer] Sent file: ${file.name} (${file.size} bytes, ${totalChunks} chunks)`);
  }

  private generateFileId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substring(2);
  }

  private async calculateSHA256(file: File): Promise<string> {
    const arrayBuffer = await file.arrayBuffer();
    const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
  }

  disconnect() {
    this.timer && clearInterval(this.timer);
    this.reconnectTimer && clearTimeout(this.reconnectTimer);
    this.ws?.close();
  }
}