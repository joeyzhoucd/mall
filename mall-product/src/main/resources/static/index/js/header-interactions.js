window.addEventListener('DOMContentLoaded', function(){
  var input = document.getElementById('searchText');
  if(input){
    input.addEventListener('keydown', function(e){
      if(e.key === 'Enter'){
        document.getElementById('searchBtn')?.click();
      }
    });
  }
  var btn = document.getElementById('searchBtn');
  if(btn){
    btn.addEventListener('click', function(){
      var keyword = input && input.value ? input.value.trim() : '';
      if(!keyword) return;
      window.location.href = '/search?keyword=' + encodeURIComponent(keyword);
    });
  }
});

