<template>
  <el-container style="height: 100vh">
    <el-aside width="216px" class="side">
      <div class="logo">
        <div class="t1">政策合规智能体</div>
        <div class="t2">三资监管 · MVP v1.1</div>
      </div>
      <el-menu :default-active="active" router background-color="#0A1B33" text-color="#c7d2e3"
               active-text-color="#fff" class="menu">
        <el-menu-item index="/">合规驾驶舱</el-menu-item>
        <el-menu-item-group title="政策管理">
          <el-menu-item index="/policies">政策法规库</el-menu-item>
          <el-menu-item index="/decompose">政策拆解工作台</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="合规运行">
          <el-menu-item index="/precheck">前置预审</el-menu-item>
          <el-menu-item index="/risks">风险预警</el-menu-item>
          <el-menu-item index="/tickets">整改闭环</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="智能助手">
          <el-menu-item index="/assistant">对话助手</el-menu-item>
        </el-menu-item-group>
      </el-menu>
      <div class="ft">演示数据均为虚构</div>
    </el-aside>
    <el-container>
      <el-header class="top">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>政策合规智能体</el-breadcrumb-item>
          <el-breadcrumb-item>{{ activeName }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="rt">
          <el-tag size="small" type="primary">M5 · 三场景口径</el-tag>
          <span style="font-size: 12px; color: #909399">合规管理员</span>
        </div>
      </el-header>
      <el-main style="background: #f0f2f5">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const names = {
  '/': '合规驾驶舱', '/policies': '政策法规库', '/decompose': '政策拆解工作台',
  '/precheck': '前置预审', '/risks': '风险预警', '/tickets': '整改闭环', '/assistant': '对话助手'
}
const route = useRoute()
const active = computed(() => route.path)
const activeName = computed(() => names[route.path] || '')
</script>

<style>
html, body, #app { margin: 0; height: 100%; font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif; }
.side { background: #0A1B33; display: flex; flex-direction: column; }
.logo { padding: 20px 18px 16px; border-bottom: 1px solid rgba(255,255,255,.08); }
.logo .t1 { font-size: 16px; font-weight: 700; color: #fff; letter-spacing: 1px; }
.logo .t2 { font-size: 11px; color: #7d92b5; margin-top: 4px; }
.menu { flex: 1; border-right: none; overflow-y: auto; }
.menu .el-menu-item-group__title { color: #5d7295; font-size: 11px; padding-left: 18px; }
.ft { padding: 14px 18px; font-size: 11px; color: #54688a; border-top: 1px solid rgba(255,255,255,.08); }
.top { background: #fff; border-bottom: 1px solid #e4e9f1; display: flex; align-items: center; justify-content: space-between; }
.rt { display: flex; align-items: center; gap: 12px; }
.pt { font-size: 18px; margin: 0 0 4px; }
.pdesc { color: #7b8aa0; font-size: 12.5px; margin: 0 0 18px; }
</style>
