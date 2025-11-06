// Dynamically load Swiper from CDN; silent if blocked
(function(){
  var css = document.createElement('link');
  css.rel = 'stylesheet';
  css.href = 'https://cdn.jsdelivr.net/npm/swiper@9/swiper-bundle.min.css';
  document.head.appendChild(css);
  var js = document.createElement('script');
  js.src = 'https://cdn.jsdelivr.net/npm/swiper@9/swiper-bundle.min.js';
  document.head.appendChild(js);
})();

