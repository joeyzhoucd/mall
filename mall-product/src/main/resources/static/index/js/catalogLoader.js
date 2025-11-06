// Expand/collapse for second/third level categories on click (mobile friendly)
document.addEventListener('click', function(e){
  var t = e.target;
  if(t && t.matches('.category > li > a, .children > li > strong > a')){
    var next = t.parentElement.querySelector(':scope > .children');
    if(next){
      e.preventDefault();
      var shown = next.style.display !== 'none';
      next.style.display = shown ? 'none' : 'block';
    }
  }
});

