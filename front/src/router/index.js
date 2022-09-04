import Vue from 'vue';
import VueRouter from 'vue-router';

Vue.use(VueRouter);

const router = new VueRouter({
  mode: 'hash',
  base: process.env.BASE_URL,
  scrollBehavior() {
    return { x: 0, y: 0 };
  },
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/Home.vue'),
    },
    {
      path: '/user-profile',
      name: 'user-profile',
      component: () => import('@/views/UserProfile.vue'),
    },
    {
      path: '/borrow-book',
      name: 'borrow-book',
      component: () => import('@/views/BorrowBook.vue'),
    },
    {
      path: '/return-book',
      name: 'return-book',
      component: () => import('@/views/ReturnBook.vue'),
    },
    {
      path: '/user-management',
      name: 'user-management',
      component: () => import('@/views/UserManagement.vue'),
      meta: {
        pageTitle: '使用者管理',
        breadcrumb: [
          {
            text: '系統管理',
            active: true,
          },
          {
            text: '使用者管理',
            active: true,
          },
        ],
      },
    },
    {
      path: '/group-management',
      name: 'group-management',
      component: () => import('@/views/GroupManagement.vue'),
      meta: {
        pageTitle: '群組管理',
        breadcrumb: [
          {
            text: '系統管理',
            active: true,
          },
          {
            text: '群組管理',
            active: true,
          },
        ],
      },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: {
        layout: 'full',
      },
    },
    {
      path: '/error-404',
      name: 'error-404',
      component: () => import('@/views/error/Error404.vue'),
      meta: {
        layout: 'full',
      },
    },
    {
      path: '*',
      redirect: 'error-404',
    },
  ],
});

export default router;
