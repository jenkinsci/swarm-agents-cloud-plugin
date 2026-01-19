(function() {
    'use strict';

    // Get rootUrl from head element
    const rootUrl = document.head.getAttribute("data-rooturl") || '';

    function refreshCloud(cloudName) {
        fetch(rootUrl + '/swarm-dashboard/refresh?cloud=' + encodeURIComponent(cloudName), {
            method: 'POST',
            headers: crumb.wrap({})
        }).then(function() {
            location.reload();
        }).catch(function(err) {
            notificationBar.show('Failed to refresh: ' + err, notificationBar.ERROR);
        });
    }

    function removeService(cloudName, serviceId) {
        dialog.confirm('Are you sure you want to remove this service?', function() {
            fetch(rootUrl + '/swarm-dashboard/removeService?cloud=' + encodeURIComponent(cloudName) +
                  '&serviceId=' + encodeURIComponent(serviceId), {
                method: 'POST',
                headers: crumb.wrap({})
            }).then(function() {
                location.reload();
            }).catch(function(err) {
                notificationBar.show('Failed to remove service: ' + err, notificationBar.ERROR);
            });
        });
    }

    // Event delegation for refresh buttons
    document.addEventListener('click', function(e) {
        if (e.target.classList.contains('js-refresh-cloud')) {
            var cloudName = e.target.getAttribute('data-cloud-name');
            if (cloudName) refreshCloud(cloudName);
        }
        if (e.target.classList.contains('js-remove-service')) {
            var cloudName = e.target.getAttribute('data-cloud-name');
            var serviceId = e.target.getAttribute('data-service-id');
            if (cloudName && serviceId) removeService(cloudName, serviceId);
        }
    });

    // Auto-refresh every 30 seconds
    setTimeout(function() { location.reload(); }, 30000);
})();
