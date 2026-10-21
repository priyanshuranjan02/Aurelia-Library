import { requestJson } from "/js/api/http.js";
export const studentsApi = {
  list: () => requestJson("/api/students"),
  create: data => requestJson("/api/students", { method: "POST", body: JSON.stringify(data) }),
  update: (id, data) => requestJson(`/api/students/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  delete: id => requestJson(`/api/students/${id}`, { method: "DELETE" })
};
