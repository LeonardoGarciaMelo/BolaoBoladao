export const apiBaseUrl = import.meta.env.PUBLIC_API_BASE_URL || "http://localhost:8080/api";
const tokenKey = "bolao.access-token";

export const getAccessToken = () => sessionStorage.getItem(tokenKey);
export const clearAccessToken = () => sessionStorage.removeItem(tokenKey);
