import { requestJson } from "/js/api/http.js";
export const booksApi = {
  list: (search = "") => requestJson(`/api/books${search ? `?search=${encodeURIComponent(search)}` : ""}`),
  create: data => requestJson("/api/books", { method: "POST", body: JSON.stringify(data) }),
  update: (id, data) => requestJson(`/api/books/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  delete: id => requestJson(`/api/books/${id}`, { method: "DELETE" })
};
