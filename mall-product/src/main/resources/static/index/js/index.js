// Index page common scripts
function search(){
  var input = document.getElementById('searchText');
  var keyword = input ? input.value : '';
  if(!keyword) return;
  window.location.href = '/search?keyword=' + encodeURIComponent(keyword);
}

