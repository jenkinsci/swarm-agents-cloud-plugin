/*
 * Marks collapsed configuration groups that actually hold something, so a long list of
 * closed <details> does not have to be opened one by one to find the configured ones.
 *
 * "Holds something" deliberately means non-empty content rather than "differs from the
 * default": several groups (Timeouts and Retry, Health Check) consist of f:number fields
 * that always carry a default, so a differs-from-default rule would either need every
 * default duplicated in the DOM or would light up permanently. Controls that always carry
 * a value - number, select, radio, hidden - are therefore ignored, and a group counts as
 * filled when it has a non-blank text field or a checked checkbox. For Health Check that
 * matches the plugin's own hasHealthCheck(), which keys off the command alone.
 */
(function () {
    'use strict';

    var GROUP_SELECTOR = 'details.swarm-section-group';
    var BADGE_CLASS = 'swarm-section-badge';
    var BADGE_TEXT = 'configured';

    /** Controls whose value is meaningful only when the user typed or ticked something. */
    function isMeaningful(control) {
        if (control.disabled) {
            return false;
        }
        if (control.tagName === 'TEXTAREA') {
            return control.value.trim() !== '';
        }
        if (control.tagName !== 'INPUT') {
            return false;
        }
        var type = (control.getAttribute('type') || 'text').toLowerCase();
        if (type === 'checkbox') {
            return control.checked;
        }
        if (type === 'text' || type === 'password' || type === 'url' || type === 'email') {
            return control.value.trim() !== '';
        }
        // number/select/radio/hidden/file/button always carry a default - ignore them.
        return false;
    }

    /** The nearest enclosing group, so controls of a nested group are not counted twice. */
    function ownerGroup(control) {
        return control.closest(GROUP_SELECTOR);
    }

    function isFilled(group) {
        var controls = group.querySelectorAll('input, textarea');
        for (var i = 0; i < controls.length; i++) {
            var control = controls[i];
            if (ownerGroup(control) === group && isMeaningful(control)) {
                return true;
            }
        }
        return false;
    }

    function badgeFor(summary) {
        var badge = summary.querySelector('.' + BADGE_CLASS);
        if (!badge) {
            badge = document.createElement('span');
            badge.className = BADGE_CLASS;
            badge.textContent = BADGE_TEXT;
            summary.appendChild(badge);
        }
        return badge;
    }

    function refresh() {
        var groups = document.querySelectorAll(GROUP_SELECTOR);
        for (var i = 0; i < groups.length; i++) {
            var summary = groups[i].querySelector(':scope > summary');
            if (summary) {
                badgeFor(summary).hidden = !isFilled(groups[i]);
            }
        }
    }

    var pending = false;

    function scheduleRefresh() {
        if (pending) {
            return;
        }
        pending = true;
        window.requestAnimationFrame(function () {
            pending = false;
            refresh();
        });
    }

    function init() {
        refresh();
        // Typing in / ticking a control changes only its own group, but a full rescan is
        // cheap and keeps the rules in one place.
        document.addEventListener('input', scheduleRefresh);
        document.addEventListener('change', scheduleRefresh);
        // Repeatable rows, and whole cloud/template chunks, are added after load.
        new MutationObserver(scheduleRefresh).observe(document.body, {
            childList: true,
            subtree: true
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
