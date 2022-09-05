export default [
  {
    title: '儀錶板',
    icon: 'ActivityIcon',
    route: 'home',
    action: 'read',
    resource: 'Dashboard',
  },
  {
    title: '借書',
    icon: 'ActivityIcon',
    route: 'borrow-book',
    action: 'read',
    resource: 'Dashboard',
  },
  {
    title: '處理還書',
    icon: 'ActivityIcon',
    route: 'return-book',
  },
  {
    title: '我的借還書紀錄',
    icon: 'ActivityIcon',
    route: 'my-booklogs',
  },
  {
    title: '系統管理',
    icon: 'SettingsIcon',
    children: [
      {
        title: '使用者管理',
        route: 'user-management',
      },
      {
        title: '群組管理',
        route: 'group-management',
      },
    ],
  },
];
