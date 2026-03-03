"use strict";

(function () {
    const config = window.CANNYMINUTE_CONFIG || {};
    const endpointUrl = (config.leadEndpointUrl || "").trim();
    const endpointMode = (config.leadEndpointMode || "google-apps-script").toLowerCase();
    const useNoCors = Boolean(config.useNoCors);
    const debug = Boolean(config.debug);

    const yearNode = document.getElementById("year");
    if (yearNode) {
        yearNode.textContent = String(new Date().getFullYear());
    }

    initRevealAnimations();
    initBetaForm();

    function initRevealAnimations() {
        const revealNodes = Array.from(document.querySelectorAll(".reveal"));
        if (!revealNodes.length) {
            return;
        }

        if (!("IntersectionObserver" in window)) {
            revealNodes.forEach((node) => node.classList.add("is-visible"));
            return;
        }

        const observer = new IntersectionObserver(
            (entries) => {
                entries.forEach((entry) => {
                    if (entry.isIntersecting) {
                        entry.target.classList.add("is-visible");
                        observer.unobserve(entry.target);
                    }
                });
            },
            { threshold: 0.18 }
        );

        revealNodes.forEach((node) => observer.observe(node));
    }

    function initBetaForm() {
        const form = document.getElementById("beta-form");
        if (!(form instanceof HTMLFormElement)) {
            return;
        }

        const submitButton = document.getElementById("submit-button");
        const statusNode = document.getElementById("form-status");
        const emailInput = document.getElementById("email");

        if (emailInput instanceof HTMLInputElement) {
            const savedEmail = window.localStorage.getItem("cannyminute_beta_email") || "";
            if (!emailInput.value && savedEmail) {
                emailInput.value = savedEmail;
            }
        }

        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            setStatus("", "");

            const payload = buildPayload(form);
            const validationError = validatePayload(payload);
            if (validationError) {
                setStatus(validationError, "error");
                return;
            }

            if (!endpointUrl) {
                setStatus("Beta form is not configured yet. Please set leadEndpointUrl in config.js.", "error");
                return;
            }

            setSubmitting(true);
            try {
                if (debug) {
                    console.log("Submitting lead payload", payload);
                }

                await submitLead(payload);
                window.localStorage.setItem("cannyminute_beta_email", payload.email);
                form.reset();
                setStatus("Nice one. You're on the beta list.", "ok");
            } catch (error) {
                const message = error instanceof Error ? error.message : "Failed to submit. Please try again.";
                setStatus(message, "error");
            } finally {
                setSubmitting(false);
            }
        });

        function setSubmitting(isSubmitting) {
            if (!(submitButton instanceof HTMLButtonElement)) {
                return;
            }
            submitButton.disabled = isSubmitting;
            submitButton.textContent = isSubmitting ? "Saving..." : "Save my beta spot";
        }

        function setStatus(message, stateClass) {
            if (!statusNode) {
                return;
            }
            statusNode.textContent = message;
            statusNode.classList.remove("ok", "error");
            if (stateClass) {
                statusNode.classList.add(stateClass);
            }
        }
    }

    function buildPayload(form) {
        const data = new FormData(form);
        const payload = {
            firstName: (data.get("firstName") || "").toString().trim(),
            email: (data.get("email") || "").toString().trim().toLowerCase(),
            shopStyle: (data.get("shopStyle") || "").toString().trim(),
            source: (data.get("source") || "cannyminute_landing").toString().trim(),
            consent: data.get("consent") === "on",
            alphaTester: data.get("alphaTester") === "on",
            company: (data.get("company") || "").toString().trim(),
            submittedAtUtc: new Date().toISOString(),
            userAgent: navigator.userAgent
        };
        return payload;
    }

    function validatePayload(payload) {
        if (payload.company) {
            return "Submission blocked.";
        }
        if (!payload.email) {
            return "Enter an email address.";
        }
        if (!isLikelyEmail(payload.email)) {
            return "Enter a valid email address.";
        }
        if (!payload.consent) {
            return "Please confirm consent to receive beta emails.";
        }
        return "";
    }

    function isLikelyEmail(value) {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
    }

    async function submitLead(payload) {
        if (endpointMode === "google-apps-script") {
            await submitToAppsScript(payload);
            return;
        }

        await submitJson(payload);
    }

    async function submitToAppsScript(payload) {
        const body = new URLSearchParams();
        Object.entries(payload).forEach(([key, value]) => body.set(key, String(value)));

        const response = await fetch(endpointUrl, {
            method: "POST",
            body: body.toString(),
            headers: {
                "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"
            },
            mode: useNoCors ? "no-cors" : "cors"
        });

        if (response.type === "opaque") {
            return;
        }
        if (!response.ok) {
            throw new Error("Could not save your beta signup. Please try again.");
        }
    }

    async function submitJson(payload) {
        const response = await fetch(endpointUrl, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            throw new Error("Could not save your beta signup. Please try again.");
        }
    }
})();
