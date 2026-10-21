import { requestJson } from "/js/api/http.js";

const form = document.getElementById("setup-form");
const password = document.getElementById("setup-password");
const confirmPassword = document.getElementById("setup-confirm");
const errorMessage = document.getElementById("setup-error");
const successMessage = document.getElementById("setup-success");
const submitButton = document.getElementById("setup-submit");

// The invitation token is sent in the URL fragment, which browsers do not send to the server.
const token = new URLSearchParams(window.location.hash.slice(1)).get("token") || "";
if (token) {
  // Remove the credential from the visible address bar as soon as it is read.
  window.history.replaceState(null, "", window.location.pathname);
} else {
  showError("This invitation link is missing or invalid. Ask your library administrator for a new invitation.");
  form.hidden = true;
}

// Ensure the SPA CSRF cookie exists before submitting the state-changing password setup request.
const csrfReady = requestJson("/api/auth/csrf").then(() => true).catch(() => {
  showError("We couldn't start a secure setup session. Refresh this page and try again.");
  return false;
});

function showError(message) {
  errorMessage.textContent = message;
  errorMessage.hidden = false;
  successMessage.hidden = true;
}

function clearMessages() {
  errorMessage.textContent = "";
  errorMessage.hidden = true;
  successMessage.textContent = "";
  successMessage.hidden = true;
}

form.addEventListener("submit", async event => {
  event.preventDefault();
  clearMessages();
  if (!token) {
    showError("This invitation link is missing or invalid. Ask your library administrator for a new invitation.");
    return;
  }
  if (!await csrfReady) {
    showError("We couldn't start a secure setup session. Refresh this page and try again.");
    return;
  }
  if (password.value.length < 12) {
    showError("Use a password with at least 12 characters.");
    password.focus();
    return;
  }
  if (password.value !== confirmPassword.value) {
    showError("The passwords do not match.");
    confirmPassword.focus();
    return;
  }

  submitButton.disabled = true;
  const originalText = submitButton.textContent;
  submitButton.textContent = "Setting up your account…";
  try {
    const result = await requestJson("/api/librarian-invitations/accept", {
      method: "POST",
      body: JSON.stringify({ token, password: password.value })
    });
    form.hidden = true;
    successMessage.textContent = result?.message || "Your password is set. You can now sign in.";
    successMessage.hidden = false;
    submitButton.textContent = "Account activated";
    window.setTimeout(() => window.location.assign("/#login"), 1400);
  } catch (error) {
    showError(error?.message || "The invitation could not be accepted. Ask your administrator for a new link.");
    submitButton.disabled = false;
    submitButton.textContent = originalText;
  }
});
