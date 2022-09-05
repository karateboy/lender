<template>
  <div>
    <b-card title="書籍借還紀錄">
      <b-table :items="logs" :fields="fields" striped></b-table>
      ></b-card
    >
  </div>
</template>
<script lang="ts">
import axios from 'axios';
import moment from 'moment';
import Vue from 'vue';
import { mapState } from 'vuex';

interface BookLog {
  time: number;
  userId: string;
  bookId: string;
  title?: string;
  log: string;
}

export default Vue.extend({
  data() {
    let fields = [
      {
        key: 'time',
        label: '時間',
        formatter: (v: number) => moment(v).format('lll'),
      },
      {
        key: 'title',
        label: '書名',
      },
      {
        key: 'log',
        label: '紀錄',
      },
    ];
    return {
      logs: Array<BookLog>(),
      fields,
    };
  },
  computed: {
    ...mapState('user', ['userInfo']),
  },
  async mounted() {
    await this.getBookLogs();
  },
  methods: {
    async getBookLogs() {
      try {
        let ret = await axios.get(`/BookLogs/${this.userInfo._id}`);
        if (ret.status === 200) {
          this.logs = ret.data;
        }
      } catch (err) {
        console.error(err);
      }
    },
    getDateTimeStr(date: Date): string {
      return moment(date).format('lll');
    },
  },
});
</script>
