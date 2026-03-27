(function () {
    const form = document.getElementById("generation-form");
    const submitButton = document.getElementById("submit-button");
    const copyButton = document.getElementById("copy-button");
    const statusBanner = document.getElementById("status-banner");
    const statusText = document.getElementById("status-text");
    const emptyState = document.getElementById("empty-state");
    const resultPanel = document.getElementById("result-panel");
    const refinedRequirementElement = document.getElementById("refinedRequirement");
    const citationsElement = document.getElementById("citations");
    const javaTestCodeElement = document.getElementById("javaTestCode");

    function setStatus(type, message) {
        statusBanner.className = "status-banner";
        if (type) {
            statusBanner.classList.add(type);
        } else {
            statusBanner.classList.add("is-idle");
        }
        statusText.textContent = message;
    }

    function setLoading(isLoading) {
        submitButton.disabled = isLoading;
        submitButton.classList.toggle("is-loading", isLoading);
    }

    function clearResults() {
        refinedRequirementElement.textContent = "";
        citationsElement.innerHTML = "";
        javaTestCodeElement.textContent = "";
        resultPanel.hidden = true;
        emptyState.hidden = false;
    }

    function escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
    }

    function renderCitations(citations) {
        if (!Array.isArray(citations) || citations.length === 0) {
            citationsElement.innerHTML = "<p class=\"citation-id\">No citations returned.</p>";
            return;
        }

        citationsElement.innerHTML = citations.map(function (citation) {
            const type = escapeHtml(citation.type || "unknown");
            const apiId = citation.apiId ? "<span class=\"citation-id\">apiId: " + escapeHtml(citation.apiId) + "</span>" : "";
            const source = citation.source
                ? "<a class=\"citation-link\" href=\"" + escapeHtml(citation.source) + "\" target=\"_blank\" rel=\"noreferrer\">" + escapeHtml(citation.source) + "</a>"
                : "<span class=\"citation-id\">No source</span>";
            return [
                "<article class=\"citation-item\">",
                "<div class=\"citation-meta\">",
                "<span class=\"tag\">" + type + "</span>",
                apiId,
                "</div>",
                source,
                "</article>"
            ].join("");
        }).join("");
    }

    function renderResults(payload) {
        refinedRequirementElement.textContent = payload.refinedRequirement || "";
        javaTestCodeElement.textContent = payload.javaTestCode || "";
        renderCitations(payload.citations);
        emptyState.hidden = true;
        resultPanel.hidden = false;
    }

    function toIntegerOrNull(value) {
        if (!value) {
            return null;
        }
        return Number.parseInt(value, 10);
    }

    function buildRequestPayload(formData) {
        return {
            requirement: (formData.get("requirement") || "").trim(),
            referenceUrl: (formData.get("referenceUrl") || "").trim() || null,
            expectedHttpStatus: toIntegerOrNull((formData.get("expectedHttpStatus") || "").trim()),
            expectedErrorCode: (formData.get("expectedErrorCode") || "").trim() || null,
            expectedErrorDescription: (formData.get("expectedErrorDescription") || "").trim() || null
        };
    }

    function extractErrorMessage(payload, response) {
        if (payload && payload.error) {
            if (payload.error.message) {
                return payload.error.message;
            }
            if (payload.error.code) {
                return payload.error.code;
            }
        }
        return "Request failed with HTTP " + response.status + ".";
    }

    async function handleSubmit(event) {
        event.preventDefault();
        const formData = new FormData(form);
        const requestPayload = buildRequestPayload(formData);

        if (!requestPayload.requirement) {
            clearResults();
            setStatus("is-error", "Requirement is required.");
            document.getElementById("requirement").focus();
            return;
        }

        setLoading(true);
        setStatus("is-loading", "Generating testcase... this may take a short while.");

        try {
            const response = await fetch("/api/testcase/generate", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(requestPayload)
            });

            const payload = await response.json().catch(function () {
                return null;
            });

            if (!response.ok) {
                clearResults();
                setStatus("is-error", extractErrorMessage(payload, response));
                return;
            }

            renderResults(payload || {});
            setStatus("is-success", "Generation completed successfully.");
        } catch (error) {
            clearResults();
            setStatus("is-error", error && error.message ? error.message : "Network request failed.");
        } finally {
            setLoading(false);
        }
    }

    async function copyCode() {
        const code = javaTestCodeElement.textContent;
        if (!code) {
            setStatus("is-error", "No generated code available to copy.");
            return;
        }

        try {
            await navigator.clipboard.writeText(code);
            setStatus("is-success", "Java test code copied to clipboard.");
        } catch (error) {
            setStatus("is-error", "Clipboard copy failed in this browser context.");
        }
    }

    clearResults();
    form.addEventListener("submit", handleSubmit);
    copyButton.addEventListener("click", copyCode);
})();
