const AUTH_API_URL = 'http://localhost:8082/api/auth'

export interface AuthResponse {
  token: string
  username: string
}

export interface LoginCredentials {
  username: string
  password: string
}

export interface RegisterCredentials {
  username: string
  password: string
}

export class AuthService {
  static async login(credentials: LoginCredentials): Promise<AuthResponse> {
    const response = await fetch(`${AUTH_API_URL}/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(credentials),
    })

    if (!response.ok) {
      const error = await response.json()
      throw new Error(error.error || 'Login failed')
    }

    const data = await response.json()
    // Store token in localStorage
    localStorage.setItem('auth_token', data.token)
    localStorage.setItem('username', data.username)
    return data
  }

  static async register(credentials: RegisterCredentials): Promise<AuthResponse> {
    const response = await fetch(`${AUTH_API_URL}/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(credentials),
    })

    if (!response.ok) {
      const error = await response.json()
      throw new Error(error.error || 'Registration failed')
    }

    const data = await response.json()
    // Store token in localStorage
    localStorage.setItem('auth_token', data.token)
    localStorage.setItem('username', data.username)
    return data
  }

  static async verifyToken(token: string): Promise<{ valid: boolean; username: string }> {
    const response = await fetch(`${AUTH_API_URL}/verify`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ token }),
    })

    if (!response.ok) {
      return { valid: false, username: '' }
    }

    return await response.json()
  }

  static getToken(): string | null {
    return localStorage.getItem('auth_token')
  }

  static getUsername(): string | null {
    return localStorage.getItem('username')
  }

  static logout(): void {
    localStorage.removeItem('auth_token')
    localStorage.removeItem('username')
  }

  static isAuthenticated(): boolean {
    return !!this.getToken()
  }
}
