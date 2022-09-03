<template>
  <div>
    <b-card title="投資組合管理" class="text-center">
      <b-table
        responsive
        :fields="columns"
        :items="editItems"
        select-mode="single"
        selectable
        @row-selected="onInstSelected"
      >
        <template #thead-top
          ><b-tr>
            <b-td colspan="4">
              <b-button variant="gradient-success" @click="addItem"
                >新增</b-button
              >
              <b-button
                class="ml-3"
                variant="gradient-danger"
                :disabled="!Boolean(selected)"
                @click="deleteItem()"
                >刪除</b-button
              >
              <b-button
                v-ripple.400="'rgba(255, 255, 255, 0.15)'"
                variant="primary"
                class="ml-3"
                @click="save"
              >
                儲存
              </b-button>
            </b-td></b-tr
          >
        </template>
        <template #cell(selected)="{ rowSelected }">
          <template v-if="rowSelected">
            <span aria-hidden="true">&check;</span>
            <span class="sr-only">Selected</span>
          </template>
          <template v-else>
            <span aria-hidden="true">&nbsp;</span>
            <span class="sr-only">Not selected</span>
          </template>
        </template>
        <template #cell(name)="row">
          <b-form-input
            v-model="row.item.name"
            style="min-width: 130px"
            size="md"
            @change="markDirty(row.item)"
          />
        </template>
        <template #cell(birthday)="row">
          <date-picker
            v-model="row.item.birthday"
            type="date"
            format="YYYY-MM-DD"
            value-type="date"
            :show-second="false"
          />
        </template>
        <template #cell(location)="row">
          <b-form-input
            v-model="row.item.location"
            style="min-width: 100px"
            size="md"
            @change="markDirty(row.item)"
          />
        </template>
      </b-table>
    </b-card>
  </div>
</template>
<script lang="ts">
import Vue from 'vue';
const Ripple = require('vue-ripple-directive');
import { mapActions, mapState } from 'vuex';
import axios from 'axios';
import { InvestItem } from './types';
import DatePicker from 'vue2-datepicker';
import 'vue2-datepicker/index.css';
import 'vue2-datepicker/locale/zh-tw';
import ToastificationContent from '@core/components/toastification/ToastificationContent.vue';

interface EditInvestItem extends InvestItem {
  dirty: boolean;
}
export default Vue.extend({
  components: {
    DatePicker,
  },
  directives: {
    Ripple,
  },
  data() {
    const columns = [
      {
        key: 'selected',
        label: '選擇',
      },
      {
        key: 'name',
        label: '資產名稱',
        sortable: true,
      },
      {
        key: 'birthday',
        label: '相關日期',
        sortable: true,
      },
      {
        key: 'location',
        label: '地點',
        sortable: true,
      },
    ];

    let editItems = Array<EditInvestItem>();
    let selected: EditInvestItem | undefined;
    return {
      display: false,
      columns,
      editItems,
      selected,
    };
  },
  computed: {
    ...mapState('user', ['userInfo']),
  },
  async mounted() {
    this.copySetupFromProfile();
  },
  methods: {
    ...mapActions('user', ['getUserInfo']),
    async save() {
      try {
        const res = await axios.post(
          `/User/${this.userInfo._id}/items`,
          this.editItems,
        );
        if (res.status === 200) {
          this.$bvModal.msgBoxOk('成功');
          await this.getUserInfo();
          this.copySetupFromProfile();
        }
      } catch (err) {
        throw new Error('failed to save items');
      }
    },
    copySetupFromProfile() {
      this.editItems.splice(0, this.editItems.length);
      for (let p of this.userInfo.items) {
        let item: EditInvestItem = {
          name: p.name,
          birthday: new Date(p.birthday),
          location: p.location,
          dirty: false,
        };
        this.editItems.push(item);
      }
    },
    addItem() {
      this.editItems.push({
        name: '',
        birthday: new Date(),
        location: '台灣',
        dirty: true,
      });
    },
    markDirty(item: any) {
      item.dirty = true;
    },
    deleteItem(item: EditInvestItem) {
      if (this.selected === undefined) return;
      let idx = this.editItems.indexOf(this.selected);
      if (idx !== -1) {
        this.editItems.splice(idx, 1);
      }
    },
    onInstSelected(items: Array<EditInvestItem>) {
      this.selected = items[0];
    },
  },
});
</script>

<style></style>
