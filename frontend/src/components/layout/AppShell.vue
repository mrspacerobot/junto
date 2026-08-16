<template>
  <div class="h-screen overflow-hidden">
    <Sidebar class="hidden md:block" />
    <div class="md:ml-[220px] h-full flex flex-col">
      <TopNav class="hidden md:flex flex-shrink-0" />
      <main class="flex-1 overflow-hidden flex flex-col min-h-0 bg-warm-100">
        <router-view />
      </main>
    </div>
    <MobileNavBar />
  </div>
</template>

<script setup lang="ts">
import Sidebar from './Sidebar.vue';
import TopNav from './TopNav.vue';
import MobileNavBar from './MobileNavBar.vue';
</script>
