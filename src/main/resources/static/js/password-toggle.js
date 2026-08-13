/*
 * Adds a show/hide control to every password field on the page.
 *
 * Lives in its own file rather than inline because the Content-Security-Policy sets
 * script-src 'self', which blocks inline <script> blocks. Same-origin files are allowed.
 *
 * Built in JavaScript rather than written into each template so that any password field
 * added later gets the control automatically.
 */
(function () {
    'use strict';

    var EYE_OPEN =
        '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" ' +
        'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';

    var EYE_CLOSED =
        '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" ' +
        'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>' +
        '<path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>' +
        '<path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>';

    function attachToggle(input) {
        if (input.dataset.toggleAttached === 'true') {
            return;
        }
        input.dataset.toggleAttached = 'true';

        // Wrap the input so the button can sit inside its right-hand edge.
        var wrapper = document.createElement('div');
        wrapper.className = 'password-wrapper';
        input.parentNode.insertBefore(wrapper, input);
        wrapper.appendChild(input);

        var button = document.createElement('button');
        // Not a submit button -- inside a form, the default type would post it.
        button.type = 'button';
        button.className = 'password-toggle';
        button.innerHTML = EYE_OPEN;
        button.setAttribute('aria-label', 'Show password');
        button.setAttribute('aria-pressed', 'false');

        button.addEventListener('click', function () {
            var revealed = input.getAttribute('type') === 'text';
            input.setAttribute('type', revealed ? 'password' : 'text');
            button.innerHTML = revealed ? EYE_OPEN : EYE_CLOSED;
            button.setAttribute('aria-label', revealed ? 'Show password' : 'Hide password');
            button.setAttribute('aria-pressed', revealed ? 'false' : 'true');
            // Keep the caret where the user left it.
            input.focus();
        });

        wrapper.appendChild(button);
    }

    function init() {
        var fields = document.querySelectorAll('input[type="password"]');
        for (var i = 0; i < fields.length; i++) {
            attachToggle(fields[i]);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
