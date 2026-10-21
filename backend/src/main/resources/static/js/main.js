import { openModal } from "/components/modal.js";
import { booksApi } from "/js/api/books-api.js";
import { studentsApi } from "/js/api/students-api.js";
import { librariansApi } from "/js/api/librarians-api.js";
import { borrowApi } from "/js/api/borrow-api.js";
import { dashboardApi } from "/js/api/dashboard-api.js";
import { authApi } from "/js/api/auth-api.js";

const pages = [...document.querySelectorAll("[data-page]")];
const sidebar = document.getElementById("app-sidebar");
const navScrim = document.querySelector(".nav-scrim");
const menuButton = document.querySelector("[data-action='toggle-nav']");
const loginForm = document.getElementById("login-form");
const registrationForm = document.getElementById("registration-form");
const rolePages = {
  ADMIN: ["dashboard", "books", "students", "librarians", "borrow-records", "profile"],
  LIBRARIAN: ["dashboard", "books", "students", "borrow-records", "profile"],
  STUDENT: ["books", "borrow-records", "profile"]
};
const sectionNames = {
  dashboard: "Overview",
  books: "Book collection",
  students: "Readers & students",
  librarians: "Librarians",
  "borrow-records": "Borrow records",
  profile: "My profile",
  home: "Welcome",
  login: "Welcome back",
  register: "Create student account"
};
const state = {
  user: null,
  dashboard: null,
  profile: null,
  books: [],
  students: [],
  librarians: [],
  records: [],
  bookSearch: "",
  studentSearch: "",
  librarianSearch: "",
  recordFilter: ""
};

const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, character => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
})[character]);
const initials = value => String(value || "A").trim().split(/\s+/).slice(0, 2).map(part => part.charAt(0)).join("").toUpperCase() || "A";
const dateObject = value => value ? new Date(`${String(value).slice(0, 10)}T00:00:00`) : null;
const formatDate = value => {
  const date = dateObject(value);
  return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" }).format(date) : "—";
};
const formatShortDate = value => {
  const date = dateObject(value);
  return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(date) : "Date not set";
};
const roleLabel = role => ({ ADMIN: "Administrator", LIBRARIAN: "Librarian", STUDENT: "Student" })[role] || "Library member";

function toast(message, type = "success") {
  const region = document.getElementById("toast-region");
  const item = document.createElement("div");
  item.className = `toast ${type === "error" ? "error" : type === "info" ? "info" : ""}`.trim();
  item.setAttribute("role", type === "error" ? "alert" : "status");
  item.textContent = message || "Something went wrong. Please try again.";
  region.append(item);
  window.setTimeout(() => item.remove(), 4200);
}

function closeMobileNav() {
  document.body.classList.remove("nav-open");
  sidebar.classList.remove("is-open");
  navScrim.classList.remove("is-visible");
  menuButton?.setAttribute("aria-expanded", "false");
}

function route() {
  const loggedIn = Boolean(state.user);
  document.body.classList.toggle("authenticated", loggedIn);
  const allowed = rolePages[state.user?.role] || [];
  const requested = window.location.hash.slice(1) || (loggedIn ? (state.user?.role === "STUDENT" ? "profile" : "dashboard") : "home");
  const publicPages = ["home", "login", "register"];
  let activePage = "home";

  if (loggedIn) {
    activePage = allowed.includes(requested) ? requested : (allowed[0] || "profile");
    if (requested !== activePage) window.history.replaceState(null, "", `#${activePage}`);
  } else {
    activePage = publicPages.includes(requested) ? requested : "home";
    if (requested !== activePage) window.history.replaceState(null, "", `#${activePage}`);
  }

  pages.forEach(page => page.classList.toggle("active", page.dataset.page === activePage));
  document.querySelectorAll("[data-route]").forEach(link => {
    const isActive = link.dataset.route === activePage;
    link.classList.toggle("active", isActive);
    if (isActive) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
    const onlyRole = link.dataset.role;
    const allowedRoles = (link.dataset.roles || "").split(",").filter(Boolean);
    link.hidden = Boolean((onlyRole && state.user?.role !== onlyRole) ||
      (allowedRoles.length && !allowedRoles.includes(state.user?.role)));
  });
  // Apply role visibility to action controls as well as navigation links.
  document.querySelectorAll("[data-role], [data-roles]").forEach(element => {
    const onlyRole = element.dataset.role;
    const allowedRoles = (element.dataset.roles || "").split(",").filter(Boolean);
    element.hidden = Boolean((onlyRole && state.user?.role !== onlyRole) ||
      (allowedRoles.length && !allowedRoles.includes(state.user?.role)));
  });
  const studentLoansView = activePage === "borrow-records" && state.user?.role === "STUDENT";
  document.getElementById("topbar-section").textContent = studentLoansView ? "My loans" : (sectionNames[activePage] || "Library");
  const borrowNavLabel = document.getElementById("borrow-nav-label");
  if (borrowNavLabel) borrowNavLabel.textContent = state.user?.role === "STUDENT" ? "My loans" : "Borrow records";
  const recordsTitle = document.getElementById("records-title");
  if (recordsTitle) recordsTitle.textContent = state.user?.role === "STUDENT" ? "My loans" : "Borrow records";
  const recordsDescription = document.getElementById("records-description");
  if (recordsDescription) recordsDescription.textContent = state.user?.role === "STUDENT"
    ? "Keep track of the books you've borrowed and their return status."
    : "Every loan has a story. Keep each one accounted for.";
  document.querySelectorAll("[data-user-name]").forEach(element => element.textContent = state.user?.displayName || state.user?.username || "Account");
  document.querySelectorAll("[data-user-role]").forEach(element => element.textContent = roleLabel(state.user?.role));
  document.getElementById("header-avatar").textContent = initials(state.user?.displayName || state.user?.username);
  closeMobileNav();
}

function setLoginError(message) {
  const error = document.getElementById("login-error");
  error.textContent = message || "Sign in failed. Please check your details and try again.";
  error.hidden = !message;
}

function clearRegistrationErrors() {
  document.querySelectorAll("[data-register-error-for]").forEach(element => { element.textContent = ""; });
  const error = document.getElementById("registration-error");
  error.textContent = "";
  error.hidden = true;
}

function showRegistrationErrors(message, fieldErrors = []) {
  clearRegistrationErrors();
  let hasFieldError = false;
  fieldErrors.forEach(({ field, message: fieldMessage }) => {
    const target = document.querySelector(`[data-register-error-for="${CSS.escape(field || "")}"]`);
    if (target) {
      target.textContent = fieldMessage || "Please check this field.";
      hasFieldError = true;
    }
  });
  const summary = document.getElementById("registration-error");
  summary.textContent = message || (hasFieldError ? "Please correct the highlighted fields." : "We couldn't create your account. Please try again.");
  summary.hidden = false;
}

function handleRequestError(error, fallback = "The request could not be completed.") {
  if (error?.status === 401 && state.user) {
    state.user = null;
    state.profile = null;
    window.location.hash = "login";
    route();
    toast("Your session has expired. Please sign in again.", "error");
    return;
  }
  toast(error?.message || fallback, "error");
}

async function loadWorkspace({ quiet = false } = {}) {
  if (!state.user) return;
  const role = state.user.role;
  if (role === "STUDENT") {
    const results = await Promise.allSettled([
      booksApi.list(),
      borrowApi.listMine(),
      authApi.profile()
    ]);
    state.books = results[0].status === "fulfilled" ? results[0].value : [];
    state.records = results[1].status === "fulfilled" ? results[1].value : [];
    state.profile = results[2].status === "fulfilled" ? results[2].value : null;
    syncProfileToCurrentUser(state.profile);
    renderBooks(state.books);
    renderRecords(state.records);
    renderProfile(state.profile || state.user || {});
    const failed = results.find(result => result.status === "rejected");
    if (failed && !quiet) handleRequestError(failed.reason, "Some of your library information could not be loaded.");
    return;
  }

  const jobs = [
    ["dashboard", () => dashboardApi.get()],
    ["books", () => booksApi.list()],
    ["students", () => studentsApi.list()],
    ["records", () => borrowApi.list()],
    ["profile", () => authApi.profile()]
  ];
  if (role === "ADMIN") jobs.push(["librarians", () => librariansApi.list()]);
  const results = await Promise.allSettled(jobs.map(([, request]) => request()));
  let failure = null;
  results.forEach((result, index) => {
    const [key] = jobs[index];
    if (result.status === "fulfilled") {
      state[key] = result.value;
    } else {
      state[key] = key === "dashboard" || key === "profile" ? null : [];
      failure ||= result.reason;
    }
  });
  syncProfileToCurrentUser(state.profile);
  renderWorkspace();
  if (failure && !quiet) handleRequestError(failure, "Some library data could not be loaded.");
}

function renderWorkspace() {
  renderDashboard(state.dashboard || {});
  renderBooks(state.books);
  renderStudents(state.students);
  renderLibrarians(state.librarians);
  renderRecords(state.records);
  renderProfile(state.profile || state.user || {});
}

function renderDashboard(data) {
  const metrics = [
    { label: "Titles in collection", value: data.totalBooks, foot: "Every shelf, in one place", icon: "▤", tone: "violet" },
    { label: "Ready to borrow", value: data.availableBooks, foot: "Waiting for a reader", icon: "✧", tone: "jade" },
    { label: "Active loans", value: data.borrowedBooks, foot: "Currently checked out", icon: "↗", tone: "coral" },
    { label: "Student readers", value: data.totalStudents, foot: "Part of your community", icon: "♙", tone: "amber" },
    { label: "Librarians", value: data.totalLibrarians, foot: "Caring for the collection", icon: "✳", tone: "blue" }
  ];
  document.getElementById("dashboard-stats").innerHTML = metrics.map(metric => `
    <article class="stat-card ${metric.tone}">
      <div class="stat-card-top"><span class="stat-label">${metric.label}</span><span class="stat-icon" aria-hidden="true">${metric.icon}</span></div>
      <strong class="stat-value">${Number.isFinite(Number(metric.value)) ? Number(metric.value) : 0}</strong>
      <span class="stat-foot">${metric.foot}</span>
    </article>`).join("");
  const displayName = state.user?.displayName || state.user?.username || "reader";
  document.getElementById("dashboard-greeting").textContent = displayName.split(/\s+/)[0] + ".";
  document.getElementById("hero-available-count").textContent = Number(data.availableBooks) || 0;
  document.getElementById("active-loan-count").textContent = Number(data.borrowedBooks) || 0;
  renderRecentRecords();
}

function actionButtons(kind, id) {
  return `<div class="row-actions"><button class="row-action" type="button" data-edit-kind="${kind}" data-id="${Number(id)}" aria-label="Edit ${kind} ${Number(id)}" title="Edit">↗ <span>Edit</span></button><button class="row-action danger" type="button" data-delete-kind="${kind}" data-id="${Number(id)}" aria-label="Delete ${kind} ${Number(id)}" title="Delete">×</button></div>`;
}

function renderBooks(rows) {
  const body = document.getElementById("books-table");
  const books = Array.isArray(rows) ? rows : [];
  document.getElementById("book-count-label").textContent = `${books.length} ${books.length === 1 ? "title" : "titles"} in the collection`;
  document.getElementById("books-visible-count").textContent = `${books.length} shown`;
  if (!books.length) {
    body.innerHTML = `<tr><td colspan="5" class="empty-state">${state.bookSearch ? "No titles match that search. Try another word or author." : "The catalogue is waiting for its first title."}</td></tr>`;
    return;
  }
  const isStudent = state.user?.role === "STUDENT";
  body.innerHTML = books.map((book, index) => {
    const actions = isStudent
      ? (book.available
          ? `<div class="row-actions"><button class="row-action borrow-action" type="button" data-borrow-book="${Number(book.id)}" aria-label="Borrow ${escapeHtml(book.title)}">＋ <span>Borrow</span></button></div>`
          : `<span class="table-meta">Currently on loan</span>`)
      : actionButtons("book", book.id);
    return `<tr>
      <td><div class="entity-title"><span class="entity-cover ${["", "cover-jade", "cover-coral", "cover-blue", "cover-lilac"][index % 5]}" aria-hidden="true">▤</span><span class="entity-name"><strong>${escapeHtml(book.title)}</strong><small>${escapeHtml(book.author || "Author not listed")} · #${Number(book.id)}</small></span></div></td>
      <td>${escapeHtml(book.isbn || "—")}</td><td>${formatDate(book.publishedDate)}</td>
      <td><span class="status-pill ${book.available ? "available" : "borrowed"}">${book.available ? "Available" : "On loan"}</span></td>
      <td>${actions}</td></tr>`;
  }).join("");
}

function renderStudents(rows) {
  const body = document.getElementById("students-table");
  const query = state.studentSearch.trim().toLowerCase();
  const filtered = (Array.isArray(rows) ? rows : []).filter(student => !query || [student.name, student.email, student.username, student.phone].some(value => String(value || "").toLowerCase().includes(query)));
  document.getElementById("students-visible-count").textContent = `${filtered.length} ${filtered.length === 1 ? "reader" : "readers"}`;
  body.innerHTML = filtered.length ? filtered.map((student, index) => `<tr>
    <td><div class="entity-title"><span class="entity-cover ${["cover-jade", "", "cover-blue", "cover-lilac"][index % 4]}" aria-hidden="true">${escapeHtml(initials(student.name))}</span><span class="entity-name"><strong>${escapeHtml(student.name)}</strong><small>Reader #${Number(student.id)}</small></span></div></td>
    <td><div class="contact-stack"><span>${escapeHtml(student.email)}</span><small>${escapeHtml(student.phone)}</small></div></td><td>${escapeHtml(student.username)}</td><td>${actionButtons("student", student.id)}</td></tr>`).join("") : `<tr><td colspan="4" class="empty-state">${query ? "No readers match that search." : "No students have been added yet."}</td></tr>`;
}

function renderLibrarians(rows) {
  const body = document.getElementById("librarians-table");
  const query = state.librarianSearch.trim().toLowerCase();
  const filtered = (Array.isArray(rows) ? rows : []).filter(person => !query || [person.name, person.username, person.phone, person.email].some(value => String(value || "").toLowerCase().includes(query)));
  document.getElementById("librarians-visible-count").textContent = `${filtered.length} ${filtered.length === 1 ? "librarian" : "librarians"}`;
  body.innerHTML = filtered.length ? filtered.map((person, index) => `<tr>
    <td><div class="entity-title"><span class="entity-cover ${["", "cover-blue", "cover-lilac"][index % 3]}" aria-hidden="true">${escapeHtml(initials(person.name))}</span><span class="entity-name"><strong>${escapeHtml(person.name)}</strong><small>Staff #${Number(person.id)}</small></span></div></td>
    <td>${Number(person.age) || "—"}</td><td>${escapeHtml(person.phone)}</td><td>${escapeHtml(person.email || "—")}</td><td>${escapeHtml(person.username)}</td><td>${actionButtons("librarian", person.id)}</td></tr>`).join("") : `<tr><td colspan="6" class="empty-state">${query ? "No librarians match that search." : "No librarians have been added yet."}</td></tr>`;
}

function statusPill(status) {
  const normalized = String(status || "").toUpperCase();
  return `<span class="status-pill ${normalized === "RETURNED" ? "returned" : normalized === "BORROWED" ? "borrowed" : "overdue"}">${normalized === "RETURNED" ? "Returned" : normalized === "BORROWED" ? "On loan" : escapeHtml(normalized || "Unknown")}</span>`;
}

function renderRecords(rows) {
  const allRecords = Array.isArray(rows) ? rows : [];
  const active = allRecords.filter(record => record.status === "BORROWED");
  const returned = allRecords.filter(record => record.status === "RETURNED");
  document.getElementById("active-loan-total").textContent = active.length;
  document.getElementById("returned-loan-total").textContent = returned.length;
  const navigationLoanCount = document.getElementById("active-loan-count");
  if (navigationLoanCount) navigationLoanCount.textContent = active.length;
  const displayed = allRecords.filter(record => !state.recordFilter || record.status === state.recordFilter);
  const body = document.getElementById("records-table");
  body.innerHTML = displayed.length ? displayed.map((record, index) => `<tr>
    <td><div class="entity-title"><span class="entity-cover ${["", "cover-jade", "cover-coral", "cover-blue"][index % 4]}" aria-hidden="true">▤</span><span class="entity-name"><strong>${escapeHtml(record.bookTitle)}</strong><small>Book #${Number(record.bookId)} · Loan #${Number(record.id)}</small></span></div></td>
    <td><div class="contact-stack"><span>${escapeHtml(record.borrowerName)}</span><small>${escapeHtml(record.borrowerEmail)}</small></div></td>
    <td>${formatDate(record.borrowDate)}</td><td>${formatDate(record.returnDate)}</td><td>${statusPill(record.status)}</td>
    <td>${record.status === "BORROWED"
      ? (state.user?.role === "STUDENT"
          ? `<span class="table-meta">On loan</span>`
          : `<div class="row-actions"><button class="row-action return-action" type="button" data-return="${Number(record.id)}" aria-label="Return ${escapeHtml(record.bookTitle)}">✓ <span>Return</span></button></div>`)
      : `<span class="table-meta">Complete</span>`}</td></tr>`).join("") : `<tr><td colspan="6" class="empty-state">${state.recordFilter === "BORROWED" ? "No books are currently on loan." : state.recordFilter === "RETURNED" ? "No returned books yet." : "No circulation records yet. Your first loan will appear here."}</td></tr>`;
  renderRecentRecords();
}

function renderRecentRecords() {
  const root = document.getElementById("recent-records");
  if (!root) return;
  const latest = [...state.records].sort((a, b) => Number(b.id) - Number(a.id)).slice(0, 4);
  root.innerHTML = latest.length ? latest.map((record, index) => `<div class="recent-row"><span class="recent-cover" aria-hidden="true">${["▤", "✧", "▤", "◈"][index % 4]}</span><span class="recent-row-copy"><strong>${escapeHtml(record.bookTitle)}</strong><small>${escapeHtml(record.borrowerName)} · ${record.status === "RETURNED" ? "Returned" : "On loan"}</small></span><span class="recent-row-meta">${statusPill(record.status)}<time>${formatShortDate(record.returnDate || record.borrowDate)}</time></span></div>`).join("") : `<div class="recent-empty"><span aria-hidden="true">✧</span><strong>No circulation yet</strong><span>Your recent loans will show here when a book is borrowed.</span></div>`;
}

function syncProfileToCurrentUser(profile) {
  if (!state.user || !profile) return;
  state.user = {
    ...state.user,
    username: profile.username || state.user.username,
    displayName: profile.displayName || profile.username || state.user.displayName || state.user.username,
    role: profile.role || state.user.role
  };
}

function renderProfile(profile) {
  const current = profile || state.user || {};
  const name = current.displayName || current.username || "Library member";
  const username = current.username || "—";
  const role = current.role || state.user?.role || "MEMBER";
  const letter = initials(name);
  document.getElementById("profile-avatar").textContent = letter;
  document.getElementById("profile-display-name").textContent = name;
  document.getElementById("profile-welcome").textContent = `Signed in as ${roleLabel(role).toLowerCase()}. Manage your details and account security below.`;
  document.getElementById("profile-role-pill").textContent = roleLabel(role).toUpperCase();

  const details = [
    ["Display name", name],
    ["Username", username],
    ...(current.email ? [["Email", current.email]] : []),
    ...(current.phone ? [["Phone", current.phone]] : []),
    ...(current.age != null ? [["Age", current.age]] : []),
    ["Access level", roleLabel(role)]
  ];
  document.getElementById("profile-card").innerHTML = `<h2>Account details</h2><p>Your role determines which library areas are available to you.</p><div class="profile-fields">${details.map(([label, value]) => `<div class="profile-field"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`).join("")}</div><div class="profile-secure-note"><span aria-hidden="true">⌑</span><p>Your account is protected by your library session. Sign out when you finish on a shared device.</p></div>`;

  const input = (label, field, value, type = "text", options = "") => `<div class="profile-input-field"><label for="profile-edit-${field}">${escapeHtml(label)}</label><input id="profile-edit-${field}" name="${field}" type="${type}" value="${escapeHtml(value ?? "")}" ${options}></div>`;
  const fields = [];
  if (role !== "ADMIN") fields.push(input("Display name", "name", current.displayName, "text", 'autocomplete="name" maxlength="100" required'));
  fields.push(input("Username", "username", username, "text", 'autocomplete="username" minlength="3" maxlength="50" required'));
  if (role === "STUDENT" || role === "LIBRARIAN") {
    fields.push(input("Email address", "email", current.email || "", "email", `autocomplete="email" maxlength="100" ${role === "STUDENT" ? "required" : ""}`));
    fields.push(input("Phone", "phone", current.phone || "", "tel", 'autocomplete="tel" maxlength="20" required'));
  }
  if (role === "LIBRARIAN") fields.push(input("Age", "age", current.age ?? "", "number", 'min="18" max="120" required'));
  document.getElementById("profile-edit-fields").innerHTML = fields.join("");
  document.getElementById("profile-edit-note").textContent = role === "ADMIN"
    ? "For administrator accounts, the username is also used as the display name. Your role cannot be changed here."
    : "Your role and permissions are managed by Aurelia and cannot be changed from your profile.";

  document.getElementById("profile-delete-username").textContent = username;
  document.getElementById("profile-delete-description").textContent = role === "STUDENT"
    ? "Permanently remove your login and profile. You must return currently borrowed books first; past circulation history will be retained with your personal details anonymized."
    : role === "ADMIN"
      ? "Permanently remove this administrator account. At least one active administrator must remain, and invitation history will be preserved without your account reference."
      : "Permanently remove your librarian login and profile. This action cannot be undone.";
  document.getElementById("profile-delete-note").textContent = role === "STUDENT"
    ? "Your prior borrowing dates and book records remain for library reporting, but your name, email and phone are replaced with anonymous values."
    : role === "ADMIN"
      ? "This action cannot be undone. The last active administrator cannot delete their account."
      : "Your login and librarian profile will be deleted. This action cannot be undone.";
  const deleteForm = document.getElementById("profile-delete-form");
  deleteForm.dataset.username = username;
}

async function openResourceModal(kind, record = null) {
  const collections = { book: state.books, student: state.students, librarian: state.librarians };
  const row = record || null;
  openModal(kind, async values => {
    const data = { ...values };
    if (kind === "book") {
      data.publishedDate = data.publishedDate || null;
      if (row) await booksApi.update(row.id, data); else await booksApi.create(data);
    } else if (kind === "student") {
      if (row) await studentsApi.update(row.id, data); else await studentsApi.create(data);
    } else if (kind === "librarian") {
      data.age = Number(data.age);
      if (row) {
        await librariansApi.update(row.id, data);
      } else {
        await librariansApi.invite(data);
        toast(`Invitation sent to ${data.email}. Ask them to check their inbox and spam folder; they will appear in this list after activating the account.`, "success");
      }
    } else if (kind === "borrow") {
      data.bookId = Number(data.bookId);
      data.studentId = Number(data.studentId);
      data.borrowDate = data.borrowDate || new Date().toISOString().slice(0, 10);
      await borrowApi.create(data);
    }
    if (kind !== "librarian" || row) toast(row ? "Changes saved." : kind === "borrow" ? "Loan recorded. One more book in good hands." : "Added successfully.");
    await loadWorkspace({ quiet: true });
  }, { record: row, books: state.books, students: state.students, availableBooks: state.books.filter(book => book.available) });
}

async function deleteResource(kind, id) {
  const collection = { book: state.books, student: state.students, librarian: state.librarians }[kind] || [];
  const record = collection.find(item => Number(item.id) === Number(id));
  const label = record?.title || record?.name || `${kind} #${id}`;
  const warning = kind === "book" ? "A book with borrowing history may be protected from deletion." : kind === "student" ? "A student linked to borrowing history may be protected from deletion." : "This removes the librarian profile.";
  if (!window.confirm(`Delete “${label}”?\n\n${warning}`)) return;
  try {
    if (kind === "book") await booksApi.delete(id);
    else if (kind === "student") await studentsApi.delete(id);
    else if (kind === "librarian") await librariansApi.delete(id);
    toast("Record deleted.");
    await loadWorkspace({ quiet: true });
  } catch (error) {
    handleRequestError(error, "This record could not be deleted.");
  }
}

loginForm.addEventListener("submit", async event => {
  event.preventDefault();
  setLoginError("");
  const submit = loginForm.querySelector("[type='submit']");
  const username = document.getElementById("login-username").value.trim();
  const password = document.getElementById("login-password").value;
  if (!username || !password) {
    setLoginError("Enter your username and password to continue.");
    return;
  }
  submit.disabled = true;
  submit.querySelector("span:first-child").textContent = "Opening your workspace…";
  try {
    await authApi.csrf();
    state.user = await authApi.login({ username, password });
    await loadWorkspace({ quiet: true });
    const destination = state.user.role === "STUDENT" ? "profile" : "dashboard";
    window.location.hash = destination;
    route();
    toast(`Welcome back, ${(state.user.displayName || state.user.username || "reader").split(/\s+/)[0]}.`, "info");
  } catch (error) {
    setLoginError(error.message || "Sign in failed. Please check your details and try again.");
  } finally {
    submit.disabled = false;
    submit.querySelector("span:first-child").textContent = "Enter workspace";
  }
});

registrationForm.addEventListener("submit", async event => {
  event.preventDefault();
  clearRegistrationErrors();

  const submit = registrationForm.querySelector("[type='submit']");
  const values = Object.fromEntries(new FormData(registrationForm));
  const confirmPassword = values.confirmPassword;
  delete values.confirmPassword;
  const clientErrors = [];

  ["name", "email", "phone", "username", "password"].forEach(field => {
    if (!String(values[field] || "").trim()) clientErrors.push({ field, message: "This field is required." });
  });
  const emailInput = document.getElementById("register-email");
  if (values.email && emailInput.validity.typeMismatch) clientErrors.push({ field: "email", message: "Enter a valid email address." });
  if (values.password && values.password.length < 8) clientErrors.push({ field: "password", message: "Use at least 8 characters." });
  if (values.password && confirmPassword !== values.password) clientErrors.push({ field: "confirmPassword", message: "The passwords do not match." });
  if (clientErrors.length) {
    showRegistrationErrors("Please review the highlighted fields.", clientErrors);
    return;
  }

  submit.disabled = true;
  submit.querySelector("span:first-child").textContent = "Creating your account…";
  try {
    // Ensure the anonymous browser has the readable CSRF cookie before POSTing.
    await authApi.csrf();
    const created = await authApi.register(values);
    registrationForm.reset();
    document.getElementById("login-username").value = created.username || values.username;
    setLoginError("");
    window.location.hash = "login";
    route();
    toast("Your student account is ready. Sign in to continue.", "info");
  } catch (error) {
    showRegistrationErrors(error.message || "We couldn't create your account. Please try again.", error.fieldErrors || []);
  } finally {
    submit.disabled = false;
    submit.querySelector("span:first-child").textContent = "Create student account";
  }
});


const profileEditForm = document.getElementById("profile-edit-form");
profileEditForm.addEventListener("submit", async event => {
  event.preventDefault();
  if (!profileEditForm.reportValidity()) return;
  const values = Object.fromEntries(new FormData(profileEditForm));
  const payload = {
    username: String(values.username || "").trim(),
    name: values.name ? String(values.name).trim() : null,
    email: values.email ? String(values.email).trim() || null : null,
    phone: values.phone ? String(values.phone).trim() || null : null,
    age: values.age ? Number(values.age) : null
  };
  const submit = profileEditForm.querySelector("[type='submit']");
  submit.disabled = true;
  try {
    const updated = await authApi.updateProfile(payload);
    state.profile = updated;
    syncProfileToCurrentUser(updated);
    renderProfile(updated);
    route();
    toast("Your profile has been updated.", "success");
  } catch (error) {
    handleRequestError(error, "Your profile could not be updated.");
  } finally {
    submit.disabled = false;
  }
});

const profilePasswordForm = document.getElementById("profile-password-form");
profilePasswordForm.addEventListener("submit", async event => {
  event.preventDefault();
  if (!profilePasswordForm.reportValidity()) return;
  const values = Object.fromEntries(new FormData(profilePasswordForm));
  if (values.newPassword !== values.confirmPassword) {
    toast("The new passwords do not match.", "error");
    document.getElementById("profile-confirm-password").focus();
    return;
  }
  if (values.newPassword === values.currentPassword) {
    toast("Choose a new password that differs from your current password.", "error");
    return;
  }
  const submit = profilePasswordForm.querySelector("[type='submit']");
  submit.disabled = true;
  try {
    await authApi.changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword, confirmPassword: values.confirmPassword });
    profilePasswordForm.reset();
    toast("Your password has been changed.", "success");
  } catch (error) {
    handleRequestError(error, "Your password could not be changed.");
  } finally {
    submit.disabled = false;
  }
});

const profileDeleteForm = document.getElementById("profile-delete-form");
profileDeleteForm.addEventListener("submit", async event => {
  event.preventDefault();
  if (!profileDeleteForm.reportValidity()) return;
  const values = Object.fromEntries(new FormData(profileDeleteForm));
  if (values.confirmationUsername !== profileDeleteForm.dataset.username) {
    toast("Type your username exactly as shown to confirm deletion.", "error");
    document.getElementById("profile-delete-confirmation").focus();
    return;
  }
  const confirmed = window.confirm("Permanently delete your Aurelia account? This cannot be undone.");
  if (!confirmed) return;
  const submit = profileDeleteForm.querySelector("[type='submit']");
  submit.disabled = true;
  try {
    await authApi.deleteProfile({ currentPassword: values.currentPassword, confirmationUsername: values.confirmationUsername });
    state.user = null;
    state.profile = null;
    state.dashboard = null;
    state.books = [];
    state.students = [];
    state.librarians = [];
    state.records = [];
    window.location.hash = "home";
    route();
    toast("Your account has been deleted. Relevant circulation history has been anonymized and retained.", "info");
  } catch (error) {
    handleRequestError(error, "Your account could not be deleted.");
  } finally {
    submit.disabled = false;
  }
});

document.addEventListener("click", async event => {
  const action = event.target.closest("[data-action]");
  if (action) {
    switch (action.dataset.action) {
      case "toggle-nav": {
        const open = !sidebar.classList.contains("is-open");
        sidebar.classList.toggle("is-open", open);
        navScrim.classList.toggle("is-visible", open);
        document.body.classList.toggle("nav-open", open);
        action.setAttribute("aria-expanded", String(open));
        break;
      }
      case "close-nav": closeMobileNav(); break;
      case "logout":
        try {
          await authApi.logout();
          state.user = null;
          state.profile = null;
          window.location.hash = "login";
          route();
          toast("You have signed out safely.", "info");
        } catch (error) { handleRequestError(error, "Sign out could not be completed."); }
        break;
      case "toggle-password": {
        const password = document.getElementById("login-password");
        const showing = password.type === "text";
        password.type = showing ? "password" : "text";
        action.textContent = showing ? "Show" : "Hide";
        action.setAttribute("aria-label", showing ? "Show password" : "Hide password");
        break;
      }
    }
    return;
  }

  const openButton = event.target.closest("[data-open-modal]");
  if (openButton) {
    await openResourceModal(openButton.dataset.openModal);
    return;
  }
  const editButton = event.target.closest("[data-edit-kind]");
  if (editButton) {
    const kind = editButton.dataset.editKind;
    const rows = { book: state.books, student: state.students, librarian: state.librarians }[kind] || [];
    const row = rows.find(item => Number(item.id) === Number(editButton.dataset.id));
    if (row) await openResourceModal(kind, row);
    return;
  }
  const deleteButton = event.target.closest("[data-delete-kind]");
  if (deleteButton) {
    await deleteResource(deleteButton.dataset.deleteKind, deleteButton.dataset.id);
    return;
  }
  const borrowButton = event.target.closest("[data-borrow-book]");
  if (borrowButton) {
    borrowButton.disabled = true;
    try {
      await borrowApi.borrowMine({
        bookId: Number(borrowButton.dataset.borrowBook),
        borrowDate: new Date().toISOString().slice(0, 10)
      });
      toast("Book borrowed successfully. You can find it under My loans.");
      await loadWorkspace({ quiet: true });
    } catch (error) {
      handleRequestError(error, "This book could not be borrowed.");
    } finally {
      borrowButton.disabled = false;
    }
    return;
  }

  const returnButton = event.target.closest("[data-return]");
  if (returnButton) {
    returnButton.disabled = true;
    try {
      await borrowApi.returnBook(returnButton.dataset.return);
      toast("Book returned to the collection.");
      await loadWorkspace({ quiet: true });
    } catch (error) {
      handleRequestError(error, "This book could not be returned.");
    } finally {
      returnButton.disabled = false;
    }
  }
});

document.getElementById("book-search").addEventListener("input", event => {
  state.bookSearch = event.target.value;
  window.clearTimeout(window.bookSearchTimer);
  window.bookSearchTimer = window.setTimeout(async () => {
    try { renderBooks(await booksApi.list(state.bookSearch)); }
    catch (error) { handleRequestError(error, "The collection search failed."); }
  }, 180);
});
document.getElementById("student-search").addEventListener("input", event => { state.studentSearch = event.target.value; renderStudents(state.students); });
document.getElementById("librarian-search").addEventListener("input", event => { state.librarianSearch = event.target.value; renderLibrarians(state.librarians); });
document.getElementById("record-filter").addEventListener("change", event => { state.recordFilter = event.target.value; renderRecords(state.records); });
window.addEventListener("hashchange", route);
document.addEventListener("keydown", event => { if (event.key === "Escape") closeMobileNav(); });

document.getElementById("today-label").textContent = new Intl.DateTimeFormat(undefined, { weekday: "short", month: "short", day: "numeric" }).format(new Date());

// Bootstrap the CSRF cookie before asking the server to restore the current session.
authApi.csrf().then(() => authApi.me()).then(async user => {
  state.user = user;
  route();
  await loadWorkspace({ quiet: true });
  route();
}).catch(() => {
  state.user = null;
  route();
});
