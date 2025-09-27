export class WS {
  private ws?: WebSocket;
  private url: string;
  private timer?: number;
  private reconnectTimer?: number;

  constructor(url: string) {
    this.url = url;
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
        from: "frontend-dev",
        to: "server",
        ts: Date.now(),
        nonce: this.generateNonce(),
        payload: { client: "frontend-dev", pubkey: "dev-pubkey", enc_pubkey: "dev-enc-pubkey" },
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
        from: "frontend-dev",
        to: "server",
        ts: Date.now(),
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
    this.ws?.send(JSON.stringify(obj));
  }

  disconnect() {
    this.timer && clearInterval(this.timer);
    this.reconnectTimer && clearTimeout(this.reconnectTimer);
    this.ws?.close();
  }
}