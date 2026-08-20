# 把 mall-product/mall-cart/mall-order/mall-search/mall-auth 这 5 个模块里的
# src/main/resources/static/ 全部合并到一个 Nginx 镜像里，统一走 /static/** 提供服务。
#
# 背景：这些静态资源本来想靠 Spring Boot 内嵌 Tomcat 从 classpath:/static/ 直接
# served，但 Java 进程干这个既浪费资源、又不是它擅长的事——让 Nginx 专门做这件事，
# 复用同一份构建产物本身也更符合"资源和业务逻辑分开构建、分开扩缩容"的思路。
# 5 个模块各自用 css/index、css/item、css/login 这种按页面命名的子目录，
# 互相之间没有文件名冲突，可以直接摞在一起。
FROM nginx:1.25-alpine
COPY mall-product/src/main/resources/static/ /usr/share/nginx/html/static/
COPY mall-cart/src/main/resources/static/ /usr/share/nginx/html/static/
COPY mall-order/src/main/resources/static/ /usr/share/nginx/html/static/
COPY mall-search/src/main/resources/static/ /usr/share/nginx/html/static/
COPY mall-auth/src/main/resources/static/ /usr/share/nginx/html/static/
EXPOSE 80
