export default [
  {
    title: '儀錶板',
    icon: 'ActivityIcon',
    route: 'home',
  },
  {
    title: '使用者資料',
    icon: 'UserIcon',
    route: 'user-profile',
  },
  {
    title: '設定投資組合',
    icon: 'ListIcon',
    route: 'user-portfolio',
  },
  {
    title: '投資勝率查詢',
    icon: 'SearchIcon',
    route: 'winrate-query',
  },
  {
    title: '投資行事曆',
    icon: 'CalendarIcon',
    route: 'invest-calendar',
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
