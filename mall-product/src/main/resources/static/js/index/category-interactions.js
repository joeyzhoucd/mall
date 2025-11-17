// Category hover interactions - show/hide subcategories on hover
document.addEventListener('DOMContentLoaded', function() {
    var categoryItems = document.querySelectorAll('.category > li');
    categoryItems.forEach(function(item) {
        var children = item.querySelector(':scope > .children');
        if (!children) return;
        
        var hideTimer = null;
        
        // Show subcategories on hover
        function showChildren() {
            if (hideTimer) {
                clearTimeout(hideTimer);
                hideTimer = null;
            }
            children.style.display = 'block';
            item.style.backgroundColor = '#fff5f5';
        }
        
        // Hide subcategories when mouse leaves
        function hideChildren() {
            hideTimer = setTimeout(function() {
                children.style.display = 'none';
                item.style.backgroundColor = '';
            }, 100);
        }
        
        item.addEventListener('mouseenter', showChildren);
        item.addEventListener('mouseleave', hideChildren);
        
        // Keep children visible when hovering over children area
        children.addEventListener('mouseenter', function() {
            if (hideTimer) {
                clearTimeout(hideTimer);
                hideTimer = null;
            }
        });
        
        children.addEventListener('mouseleave', hideChildren);
    });
});

