/*
 * Closes a <details> dropdown when the click lands outside it.
 *
 * <details> gives us a keyboard-accessible menu with no library and no inline script,
 * which the content security policy forbids anyway. The one behaviour it lacks is
 * dismissing on an outside click, which is this file.
 */
document.addEventListener('click', function (event) {
    document.querySelectorAll('details[data-menu][open]').forEach(function (menu) {
        if (!menu.contains(event.target)) {
            menu.removeAttribute('open');
        }
    });
});

document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') {
        return;
    }
    document.querySelectorAll('details[data-menu][open]').forEach(function (menu) {
        menu.removeAttribute('open');
        var trigger = menu.querySelector('summary');
        if (trigger) {
            trigger.focus();
        }
    });
});
