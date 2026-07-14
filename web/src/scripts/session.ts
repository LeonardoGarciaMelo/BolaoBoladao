export const apiBaseUrl = import.meta.env.PUBLIC_API_BASE_URL || "http://localhost:8080/api";
const tokenKey = "bolao.access-token";

export const getAccessToken = () => sessionStorage.getItem(tokenKey);
export const clearAccessToken = () => sessionStorage.removeItem(tokenKey);
export const createIdempotencyKey = () => {
  if (typeof globalThis.crypto?.randomUUID === "function") return globalThis.crypto.randomUUID();
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};

export interface SessionUser {
  id: string;
  name: string;
  username: string;
  roles: string[];
}

let currentUserRequest: Promise<SessionUser> | undefined;

export const authenticatedFetch = async (path: string, init: RequestInit = {}) => {
  const token = getAccessToken();
  if (!token) {
    window.location.replace("/login");
    throw new Error("Sessão não encontrada");
  }
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`${apiBaseUrl}${path}`, { ...init, headers });
  if (response.status === 401) {
    clearAccessToken();
    window.location.replace("/login");
    throw new Error("Sessão expirada");
  }
  return response;
};

export const getCurrentUser = () => {
  currentUserRequest ??= authenticatedFetch("/auth/me").then(async (response) => {
    if (!response.ok) throw new Error("Não foi possível validar a sessão.");
    return response.json() as Promise<SessionUser>;
  });
  return currentUserRequest;
};

export const requireAdmin = async () => {
  const user = await getCurrentUser();
  if (!user.roles.includes("ADMIN")) {
    const error = new Error("Acesso restrito a administradores.");
    Object.assign(error, { status: 403 });
    throw error;
  }
  return user;
};
