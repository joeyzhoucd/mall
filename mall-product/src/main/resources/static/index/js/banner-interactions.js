// Initialize Swiper if available; fallback to simple interval switch
window.addEventListener('DOMContentLoaded', function(){
  var hasSwiper = typeof Swiper !== 'undefined';
  var container = document.querySelector('.swiper');
  if(!container) return;
  if(hasSwiper){
    new Swiper('.swiper', { loop:true, autoplay:{delay:3000}, pagination:{el:'.swiper-pagination', clickable:true}, navigation:{nextEl:'.swiper-button-next', prevEl:'.swiper-button-prev'} });
  } else {
    var slides = container.querySelectorAll('.swiper-slide');
    var idx = 0; function show(i){ slides.forEach((s,n)=>{ s.style.display = (n===i)?'block':'none'; }); }
    show(0); setInterval(function(){ idx = (idx+1)%slides.length; show(idx); }, 3000);
  }
});

