import { requestJson } from "/js/api/http.js";
export const authApi = {
  csrf: () => requestJson("/api/auth/csrf"),
  login: data => requestJson("/api/auth/login", { method: "POST", body: JSON.stringify(data) }),
  register: data => requestJson("/api/auth/register", { method: "POST", body: JSON.stringify(data) }),
  logout: () => requestJson("/api/auth/logout", { method: "POST" }),
  me: () => requestJson("/api/auth/me"),
  profile: () => requestJson("/api/profile"),
  updateProfile: data => requestJson("/api/profile", { method: "PATCH", body: JSON.stringify(data) }),
  changePassword: data => requestJson("/api/profile/change-password", { method: "POST", body: JSON.stringify(data) }),
  deleteProfile: data => requestJson("/api/profile/delete", { method: "POST", body: JSON.stringify(data) })
};
