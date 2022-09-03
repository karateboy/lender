<template>
  <div>
    <b-card title="您的最佳投資時點 🤟">
      <app-timeline>
        <app-timeline-item
          v-for="timing in timings"
          :key="timing.name"
          :title="`投資${timing.itemName}`"
          :subtitle="`最佳勝率 ${(timing.bestWinRate * 100).toFixed(0)}%`"
          :time="`${timing.bestDate}`"
          variant="success"
        />
      </app-timeline>
    </b-card>
  </div>
</template>
<script lang="ts">
import AppTimeline from '@core/components/app-timeline/AppTimeline.vue';
import AppTimelineItem from '@core/components/app-timeline/AppTimelineItem.vue';
import axios from 'axios';
import moment from 'moment';
import Vue from 'vue';
interface InvestTiming {
  itemName: string;
  bestDate: string;
  bestWinRate: number | undefined;
  worstDate: string;
  worstWinRate: number | undefined;
}

export default Vue.extend({
  components: {
    AppTimeline,
    AppTimelineItem,
  },
  data() {
    return {
      timings: Array<InvestTiming>(),
    };
  },
  async mounted() {
    await this.getInvestTiming();
  },
  methods: {
    async getInvestTiming() {
      try {
        const resp = await axios.get('/InvestItemTiming');
        if (resp.status === 200) {
          for (let timing of resp.data) {
            timing.bestDate = moment(timing.bestDate).format('ll');
            timing.worstDate = moment(timing.worstDate).format('ll');
            this.timings.push(timing);
          }
          this.timings.sort;
        }
      } catch (err) {
        throw new Error('failed to getInvestTiming' + err);
      }
    },
  },
});
</script>
