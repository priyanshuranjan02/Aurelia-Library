import { requestJson } from "/js/api/http.js";

export const borrowApi = {
  list: (status = "") => requestJson(`/api/borrow-records${status ? `?status=${encodeURIComponent(status)}` : ""}`),
  create: data => requestJson("/api/borrow-records", { method: "POST", body: JSON.stringify(data) }),
  listMine: (status = "") => requestJson(`/api/borrow-records/mine${status ? `?status=${encodeURIComponent(status)}` : ""}`),
  borrowMine: data => requestJson("/api/borrow-records/mine", { method: "POST", body: JSON.stringify(data) }),
  returnBook: id => requestJson(`/api/borrow-records/${id}/return`, { method: "POST" })
};
