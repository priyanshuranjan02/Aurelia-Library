const baseDefinitions = {
  book: {
    title: "Add a title",
    editTitle: "Edit book details",
    description: "Keep the catalogue details clear and consistent.",
    fields: [
      { name: "title", label: "Title", type: "text", required: true, span: "all", placeholder: "Book title" },
      { name: "author", label: "Author", type: "text", required: false, placeholder: "Author or editor" },
      { name: "isbn", label: "ISBN", type: "text", required: false, placeholder: "ISBN / catalogue number" },
      { name: "publishedDate", label: "Publication date", type: "date", required: false }
    ]
  },
  student: {
    title: "Add a student",
    editTitle: "Edit student details",
    description: "Reader contact information is copied into their loan record.",
    fields: [
      { name: "name", label: "Full name", type: "text", required: true, span: "all", placeholder: "Reader name" },
      { name: "email", label: "Email address", type: "email", required: true, placeholder: "reader@example.com" },
      { name: "phone", label: "Phone", type: "tel", required: true, placeholder: "+1 555 0100" },
      { name: "username", label: "Username", type: "text", required: true, span: "all", placeholder: "Unique sign-in name" },
      { name: "password", label: "Initial password", type: "password", required: true, span: "all", placeholder: "At least 8 characters", hint: "The initial password is stored as a secure hash." }
    ]
  },
  librarian: {
    title: "Invite a librarian",
    editTitle: "Edit librarian details",
    description: "We’ll email a one-time setup link so the librarian can choose their own password. The invitation expires after 24 hours by default; the email includes the exact expiry time.",
    fields: [
      { name: "name", label: "Full name", type: "text", required: true, span: "all", placeholder: "Librarian name" },
      { name: "email", label: "Email address", type: "email", required: true, span: "all", placeholder: "librarian@example.com", hint: "The invitation link will be sent to this address." },
      { name: "age", label: "Age", type: "number", required: true, min: 18, max: 100, placeholder: "18+" },
      { name: "phone", label: "Phone", type: "tel", required: true, placeholder: "Contact number" },
      { name: "username", label: "Username", type: "text", required: true, span: "all", placeholder: "Unique sign-in name" }
    ]
  },
  borrow: {
    title: "Record a borrow",
    editTitle: "Record a borrow",
    description: "Choose an available title and the reader taking it home.",
    fields: [
      { name: "bookId", label: "Available title", type: "select", required: true, span: "all", optionsKey: "availableBooks", placeholder: "Choose a book" },
      { name: "studentId", label: "Reader", type: "select", required: true, span: "all", optionsKey: "students", placeholder: "Choose a student" },
      { name: "borrowDate", label: "Borrow date", type: "date", required: true, defaultToday: true },
      { name: "borrowerName", label: "Reader name", type: "text", required: true, readonly: true },
      { name: "borrowerEmail", label: "Reader email", type: "email", required: true, readonly: true },
      { name: "borrowerPhone", label: "Reader phone", type: "tel", required: true, readonly: true }
    ]
  }
};

const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, character => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character]);
const todayIso = () => {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
};

function fieldOptions(field, context, selectedValue) {
  const values = context[field.optionsKey] || [];
  const isBook = field.optionsKey === "availableBooks";
  const options = values.map(item => {
    const id = isBook ? item.id : item.id;
    const label = isBook ? `${item.title}${item.author ? ` — ${item.author}` : ""} · #${item.id}` : `${item.name} · ${item.email}`;
    return `<option value="${Number(id)}" ${String(id) === String(selectedValue ?? "") ? "selected" : ""} data-name="${escapeHtml(item.name || "")}" data-email="${escapeHtml(item.email || "")}" data-phone="${escapeHtml(item.phone || "")}">${escapeHtml(label)}</option>`;
  }).join("");
  const placeholder = `<option value="" disabled ${selectedValue == null || selectedValue === "" ? "selected" : ""}>${values.length ? field.placeholder : (isBook ? "No available books" : "No students yet")}</option>`;
  return placeholder + options;
}

function fieldMarkup(field, value, context) {
  const id = `modal-${field.name}`;
  const selected = value ?? (field.defaultToday ? todayIso() : "");
  const required = field.required ? "required" : "";
  const readonly = field.readonly ? "readonly" : "";
  const minMax = `${field.min != null ? `min="${field.min}"` : ""} ${field.max != null ? `max="${field.max}"` : ""}`;
  const input = field.type === "select"
    ? `<select id="${id}" name="${field.name}" ${required}>${fieldOptions(field, context, selected)}</select>`
    : `<input id="${id}" name="${field.name}" type="${field.type}" value="${escapeHtml(selected)}" ${required} ${readonly} ${minMax} placeholder="${escapeHtml(field.placeholder || "")}" autocomplete="${field.name === "password" ? "new-password" : "off"}">`;
  return `<div class="${field.span === "all" ? "span-all" : ""}"><label for="${id}">${escapeHtml(field.label)}</label>${input}<p class="field-error" data-error="${field.name}"></p>${field.hint ? `<p class="form-hint">${escapeHtml(field.hint)}</p>` : ""}</div>`;
}

export function openModal(kind, onSubmit, context = {}) {
  const base = baseDefinitions[kind];
  if (!base) throw new Error(`Unknown form: ${kind}`);
  const record = context.record || null;
  const mode = record ? "edit" : "create";
  const definition = { ...base, fields: base.fields.filter(field => !(record && field.name === "password") && !(record && kind === "librarian" && field.name === "email")) };
  const root = document.getElementById("modal-root");
  const previousFocus = document.activeElement;
  const formMarkup = definition.fields.map(field => fieldMarkup(field, record?.[field.name], context)).join("");
  const description = kind === "librarian" && record
    ? "Update the librarian's profile details. Sending a new invitation is only needed when creating a staff account."
    : definition.description;
  const emptyBorrowOptions = kind === "borrow" && (!context.availableBooks?.length || !context.students?.length);
  root.innerHTML = `<div class="modal-backdrop" data-modal-backdrop><section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title" aria-describedby="modal-description"><div class="modal-header"><div><span class="eyebrow">${kind === "borrow" ? "CIRCULATION DESK" : mode === "edit" ? "KEEP THINGS CURRENT" : "LIBRARY WORKSPACE"}</span><h2 id="modal-title">${escapeHtml(mode === "edit" ? definition.editTitle : definition.title)}</h2><p class="modal-description" id="modal-description">${escapeHtml(description)}</p></div><button class="modal-close" type="button" aria-label="Close dialog">×</button></div>${kind === "borrow" && emptyBorrowOptions ? `<div class="empty-filter">${!context.availableBooks?.length ? "There are no available books to borrow right now." : "Add a student profile before recording a loan."}</div>` : ""}<p class="form-error" data-modal-error role="alert" hidden></p><form novalidate><div class="form-grid">${formMarkup}</div>${kind === "borrow" ? `<p class="form-hint">Borrower contact details are filled from the selected student profile and verified by the server.</p>` : ""}<div class="modal-footer"><button class="button button-secondary" type="button" data-cancel>Cancel</button><button class="button button-primary" type="submit" ${emptyBorrowOptions ? "disabled" : ""}>${mode === "edit" ? "Save changes" : kind === "borrow" ? "Record loan" : kind === "librarian" ? "Send invitation" : "Save record"}</button></div></form></section></div>`;

  const close = () => {
    root.replaceChildren();
    if (previousFocus?.isConnected) previousFocus.focus();
    document.removeEventListener("keydown", onKeyDown);
  };
  const onKeyDown = event => { if (event.key === "Escape") close(); };
  document.addEventListener("keydown", onKeyDown);
  root.querySelector(".modal-close").addEventListener("click", close);
  root.querySelector("[data-cancel]").addEventListener("click", close);
  root.querySelector("[data-modal-backdrop]").addEventListener("click", event => { if (event.target === event.currentTarget) close(); });

  const studentSelect = root.querySelector("#modal-studentId");
  const syncStudentDetails = () => {
    const selected = studentSelect?.selectedOptions?.[0];
    for (const field of ["borrowerName", "borrowerEmail", "borrowerPhone"]) {
      const control = root.querySelector(`#modal-${field}`);
      if (control) control.value = selected?.dataset[{ borrowerName: "name", borrowerEmail: "email", borrowerPhone: "phone" }[field]] || "";
    }
  };
  studentSelect?.addEventListener("change", syncStudentDetails);
  if (kind === "borrow") syncStudentDetails();
  root.querySelector("form")?.querySelector("input,select")?.focus();

  root.querySelector("form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = form.querySelector("[type='submit']");
    const summary = root.querySelector("[data-modal-error]");
    summary.hidden = true;
    root.querySelectorAll("[data-error]").forEach(error => { error.textContent = ""; });
    const values = Object.fromEntries(new FormData(form));
    let valid = true;
    for (const field of definition.fields) {
      const control = form.elements.namedItem(field.name);
      const value = String(values[field.name] ?? "").trim();
      let message = "";
      if (field.required && !value) message = `${field.label} is required.`;
      if (!message && field.type === "email" && value && !/^\S+@\S+\.\S+$/.test(value)) message = "Enter a valid email address.";
      if (!message && field.type === "password" && value.length < 8) message = "Use at least 8 characters.";
      if (!message && field.type === "number" && value && (Number(value) < Number(field.min ?? -Infinity) || Number(value) > Number(field.max ?? Infinity))) message = `Enter a value from ${field.min} to ${field.max}.`;
      const error = root.querySelector(`[data-error="${field.name}"]`);
      if (error) error.textContent = message;
      if (message) valid = false;
      if (control?.type === "select-one" && field.required && !control.value) valid = false;
    }
    if (!valid) return;

    submit.disabled = true;
    const oldText = submit.textContent;
    submit.textContent = mode === "edit" ? "Saving changes…" : kind === "librarian" ? "Sending invitation…" : "Saving…";
    try {
      await onSubmit(values);
      close();
    } catch (error) {
      const fieldErrors = Array.isArray(error?.fieldErrors) ? error.fieldErrors : [];
      fieldErrors.forEach(({ field, message }) => {
        const target = root.querySelector(`[data-error="${field}"]`);
        if (target) target.textContent = message;
      });
      const unmatched = fieldErrors.filter(({ field }) => !root.querySelector(`[data-error="${field}"]`)).map(item => item.message);
      summary.textContent = unmatched.join(" ") || (fieldErrors.length ? "Please check the highlighted fields." : error?.message || "The record could not be saved.");
      summary.hidden = false;
      submit.disabled = false;
      submit.textContent = oldText;
    }
  });
}
