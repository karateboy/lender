<template>
  <div>
    <b-card v-if="userInfo.isAdmin" title="書籍借還狀態">
      <div id="bookPie"></div
    ></b-card>
    <b-card title="還書期限 🤟">
      <app-timeline v-if="books.length !== 0">
        <app-timeline-item
          v-for="book in books"
          :key="book._id"
          :title="`${book.title}`"
          :subtitle="`${getBorrowedFromNowStr(book.lentDate)}借出`"
          :time="`${getDueDate(book.dueDate)}`"
          variant="success"
        />
      </app-timeline>
      <b-alert v-else variant="success" show
        >您沒有待還得書籍. 快開始借書吧</b-alert
      >
    </b-card>
    <b-table
      v-if="userInfo.isAdmin"
      :items="groupBooks"
      :fields="groupBookField"
      striped
    ></b-table>
  </div>
</template>
<script lang="ts">
import AppTimeline from '@core/components/app-timeline/AppTimeline.vue';
import AppTimelineItem from '@core/components/app-timeline/AppTimelineItem.vue';
import axios from 'axios';
import moment from 'moment';
import Vue from 'vue';
import { mapState } from 'vuex';
import highcharts from 'highcharts';

interface Book {
  _id: string;
  title: string;
  groudID: string;
  lender?: string;
  lentDate?: Date;
  dueDate?: Date;
}

export default Vue.extend({
  components: {
    AppTimeline,
    AppTimelineItem,
  },
  data() {
    let groupBookField = [
      {
        key: '_id',
        label: '條碼',
      },
      {
        key: 'title',
        label: '書名',
      },
      {
        key: 'lender',
        label: '狀態',
        formatter: (v?: string) => {
          if (v) return '出借';
          else return '歸還';
        },
      },
    ];
    return {
      books: Array<Book>(),
      groupBookField,
      groupBooks: Array<Book>(),
    };
  },
  computed: {
    ...mapState('user', ['userInfo']),
  },
  async mounted() {
    await this.getBorrowedBooks();
    if (this.userInfo.isAdmin) {
      await this.getGroupBooks();
      this.drawPieChart();
    }
  },
  methods: {
    async getBorrowedBooks() {
      try {
        let ret = await axios.get(`/BorrowedBooks/${this.userInfo._id}`);
        if (ret.status === 200) {
          this.books = ret.data;
        }
      } catch (err) {
        console.error(err);
      }
    },
    async getGroupBooks() {
      try {
        let ret = await axios.get(`/GroupBooks/${this.userInfo.group}`);
        if (ret.status === 200) {
          this.groupBooks = ret.data;
        }
      } catch (err) {
        console.error(err);
      }
    },
    getBorrowedFromNowStr(date?: Date): string {
      if (date === undefined) {
        return '未借出';
      } else {
        return moment(date).fromNow();
      }
    },
    getDueDate(date?: Date): string {
      if (date === undefined) {
        return '-';
      } else {
        return moment(date).format('lll');
      }
    },
    drawPieChart() {
      let series: Array<highcharts.SeriesPieOptions> = [
        {
          name: '書籍狀態',
          type: 'pie',
          data: [
            {
              name: '借出',
              y: this.groupBooks.filter(book => book.lender).length,
              selected: true,
            },
            {
              name: '歸還',
              y: this.groupBooks.filter(book => !book.lender).length,
            },
          ],
        },
      ];
      let chartData: highcharts.Options = {
        chart: {
          plotBackgroundColor: undefined,
          plotBorderWidth: undefined,
          plotShadow: false,
          type: 'pie',
        },
        title: {
          text: '書籍借還比例',
        },
        credits: {
          enabled: false,
          href: 'http://www.tazze.tw/',
        },
        colors: [
          '#7CB5EC',
          '#434348',
          '#90ED7D',
          '#F7A35C',
          '#8085E9',
          '#F15C80',
          '#E4D354',
          '#2B908F',
          '#FB9FA8',
          '#91E8E1',
          '#7CB5EC',
          '#80C535',
          '#969696',
        ],
        tooltip: {
          pointFormat: '{series.name}: <b>{point.percentage:.1f}%</b>',
        },
        accessibility: {
          point: {
            valueSuffix: '%',
          },
        },
        plotOptions: {
          pie: {
            allowPointSelect: true,
            cursor: 'pointer',
            dataLabels: {
              enabled: true,
              format: '<b>{point.name}</b>: {point.percentage:.1f} %',
            },
          },
        },
        series,
      };
      highcharts.chart('bookPie', chartData);
    },
  },
});
</script>
