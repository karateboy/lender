<template>
  <div>
    <b-card>
      <b-form @submit.prevent>
        <b-row>
          <b-col cols="12">
            <b-form-group label="投資項目" label-for="item" label-cols-md="3">
              <v-select
                id="item"
                v-model="form.items"
                label="name"
                :reduce="item => item.name"
                :options="userInfo.items"
                multiple
              />
            </b-form-group>
          </b-col>
          <b-col cols="12">
            <b-form-group
              label="查詢區間"
              label-for="dataRange"
              label-cols-md="3"
            >
              <date-picker
                id="dataRange"
                v-model="form.range"
                :range="true"
                type="datetime"
                format="YYYY-MM-DD"
                value-type="timestamp"
                :show-second="false"
              />
            </b-form-group>
          </b-col>
          <!-- submit and reset -->
          <b-col offset-md="3">
            <b-button
              v-ripple.400="'rgba(255, 255, 255, 0.15)'"
              type="submit"
              variant="primary"
              class="mr-1"
              @click="query"
            >
              查詢
            </b-button>
          </b-col>
        </b-row>
      </b-form>
    </b-card>
    <b-card v-show="display">
      <div id="chart_container" />
    </b-card>
  </div>
</template>
<style lang="scss">
@import '@core/scss/vue/libs/vue-select.scss';
</style>
<script lang="ts">
import Vue from 'vue';
import DatePicker from 'vue2-datepicker';
import 'vue2-datepicker/index.css';
import 'vue2-datepicker/locale/zh-tw';
const Ripple = require('vue-ripple-directive');
import { mapState, mapActions, mapMutations } from 'vuex';
import darkTheme from 'highcharts/themes/dark-unica';
import useAppConfig from '../@core/app-config/useAppConfig';
import moment from 'moment';
import axios from 'axios';
import highcharts from 'highcharts';

export default Vue.extend({
  components: {
    DatePicker,
  },
  directives: {
    Ripple,
  },

  data() {
    const range = [moment().valueOf(), moment().add(30, 'days').valueOf()];
    return {
      display: false,
      form: {
        items: Array<string>(),
        chartType: 'line',
        range,
      },
    };
  },
  computed: {
    ...mapState('user', ['userInfo']),
  },
  watch: {},
  async mounted() {
    const { skin } = useAppConfig();
    if (skin.value == 'dark') {
      darkTheme(highcharts);
    }
    await this.getUserInfo();
    if (this.userInfo.items.length !== 0) {
      for (let item of this.userInfo.items) this.form.items.push(item.name);
    }
  },
  methods: {
    ...mapActions('user', ['getUserInfo']),
    ...mapMutations(['setLoading']),
    async query() {
      this.setLoading({ loading: true });
      this.display = true;
      const items = encodeURIComponent(this.form.items.join(':'));
      const url = `/WinRateTrend/${items}/${this.form.range[0]}/${this.form.range[1]}`;
      const res = await axios.get(url);
      const ret = res.data;

      this.setLoading({ loading: false });
      if (this.form.chartType !== 'boxplot') {
        ret.chart = {
          type: 'spline',
          zoomType: 'x',
          panning: true,
          panKey: 'shift',
          alignTicks: false,
        };

        const pointFormatter = function pointFormatter(this: any) {
          const d = new Date(this.x);
          return `${d.toLocaleString()}:${Math.round(this.y)}%`;
        };

        ret.colors = [
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
        ];

        ret.tooltip = { valueDecimals: 2 };
        ret.legend = { enabled: true };
        ret.credits = {
          enabled: false,
          href: 'http://www.wecc.com.tw/',
        };
        ret.xAxis.type = 'datetime';
        ret.xAxis.dateTimeLabelFormats = {
          day: '%b%e日',
          week: '%b%e日',
          month: '%y年%b',
        };

        ret.plotOptions = {
          scatter: {
            tooltip: {
              pointFormatter,
            },
          },
        };
        ret.time = {
          timezoneOffset: -480,
        };
      }
      highcharts.chart('chart_container', ret);
    },
  },
});
</script>

<style></style>
