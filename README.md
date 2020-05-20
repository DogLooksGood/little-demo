# 一个小 Demo 程序用来演示 Clojure WEB 开发中的一些概念


## 构建工具

这个项目很简单，没有使用 lein. 

使用 [tools-deps](https://clojure.org/guides/getting_started) 做为依赖管理，使用 [shadow-cljs](http://shadow-cljs.org) 做前端构建。

## 运行依赖

需要电脑中具有 docker-compose, clojure, node

## 开发模式的启动

启动 mysql docker

    docker-compose up -d

更新 npm 依赖

    npm i
    
打包 npm 依赖

    npx webpack

在编辑器中选择通过 shadow-cljs 来同时启动 Clojure 和 ClojureScript REPL

## 如何 Release ？

    TBD
