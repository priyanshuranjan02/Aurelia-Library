import { requestJson } from "/js/api/http.js";
export const librariansApi = {
  list: () => requestJson("/api/librarians"),
  create: data => requestJson("/api/librarians", { method: "POST", body: JSON.stringify(data) }),
  invite: data => requestJson("/api/librarian-invitations", { method: "POST", body: JSON.stringify(data) }),
  update: (id, data) => requestJson(`/api/librarians/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  delete: id => requestJson(`/api/librarians/${id}`, { method: "DELETE" })
};
