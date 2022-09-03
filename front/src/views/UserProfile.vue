<template>
  <div>
    <b-form @submit.prevent>
      <b-form-group label="帳號:" label-for="account" label-cols="3">
        <b-input
          id="account"
          v-model="user._id"
          :state="Boolean(user._id)"
          aria-describedby="account-feedback"
          :readonly="true"
        ></b-input>
        <b-form-invalid-feedback>帳號不能是空的</b-form-invalid-feedback>
      </b-form-group>
      <b-form-group
        label="顯示名稱:"
        label-for="name"
        :state="Boolean(user.name)"
        label-cols="3"
      >
        <b-input
          id="name"
          v-model="user.name"
          :state="Boolean(user.name)"
          aria-describedby="displayName-feedback"
        ></b-input>
        <b-form-invalid-feedback>顯示名稱不能是空的</b-form-invalid-feedback>
      </b-form-group>
      <b-form-group
        :label="passwordLabel"
        label-for="password"
        :state="isPasswordValid"
        label-cols="3"
      >
        <b-input
          id="password"
          v-model="user.password"
          type="password"
          :state="isPasswordValid"
          aria-describedby="password-feedback"
        ></b-input>
        <b-form-invalid-feedback id="password-feedback">{{
          passwordInvalidReason
        }}</b-form-invalid-feedback>
      </b-form-group>
      <b-form-group label="重新輸入密碼:" label-for="password2" label-cols="3">
        <b-input
          id="password2"
          v-model="user.confirmPassword"
          type="password"
          :state="isPasswordValid"
          aria-describedby="password-feedback"
        ></b-input>
      </b-form-group>
      <b-form-group label="生日:" label-for="birthday" label-cols="3">
        <date-picker
          id="birthday"
          v-model="user.birthday"
          type="date"
          format="YYYY-MM-DD"
          value-type="date"
          :show-second="false"
        />
      </b-form-group>
      <b-row>
        <b-col offset-md="3">
          <b-button
            v-ripple.400="'rgba(255, 255, 255, 0.15)'"
            type="submit"
            variant="primary"
            class="mr-1"
            :disabled="!canUpsert"
            @click="upsert"
          >
            {{ btnTitle }}
          </b-button>
          <b-button
            v-ripple.400="'rgba(186, 191, 199, 0.15)'"
            variant="outline-secondary"
            @click="reset"
          >
            取消
          </b-button>
        </b-col>
      </b-row>
    </b-form>
  </div>
</template>

<script lang="ts">
import DatePicker from 'vue2-datepicker';
import 'vue2-datepicker/index.css';
import 'vue2-datepicker/locale/zh-tw';
import Vue, { PropType } from 'vue';
import axios from 'axios';
import { InvestItem, User } from './types';
import { mapState } from 'vuex';
const Ripple = require('vue-ripple-directive');
interface EditUser extends User {
  confirmPassword: string;
}

export default Vue.extend({
  directives: {
    Ripple,
  },
  components: {
    DatePicker,
  },

  data() {
    const user: EditUser = {
      _id: '',
      name: '',
      password: '',
      confirmPassword: '',
      birthday: new Date(),
      isAdmin: false,
      items: Array<InvestItem>(),
    };

    return {
      user,
    };
  },
  computed: {
    ...mapState('user', ['userInfo']),
    passwordLabel(): string {
      return '變更密碼:';
    },
    isPasswordValid(): boolean {
      if (this.user.password === this.user.confirmPassword) return true;
      return false;
    },
    passwordInvalidReason(): string {
      if (this.user.password !== this.user.confirmPassword) {
        return '密碼和重新輸入必須一致';
      }
      return '';
    },
    canUpsert(): boolean {
      return this.isPasswordValid;
    },
    btnTitle(): string {
      return '更新';
    },
    isMyself(): boolean {
      if (this.user._id === this.userInfo._id) return true;
      return false;
    },
    isAdmin(): boolean {
      return this.user.isAdmin;
    },
  },
  async mounted() {
    this.user._id = this.userInfo._id;
    this.user.name = this.userInfo.name;
    this.user.isAdmin = this.userInfo.isAdmin;
    this.user.birthday = new Date(this.userInfo.birthday);
    this.user.items = this.userInfo.items;
  },
  methods: {
    copyUser(user: User): void {
      const self: User = this.userInfo as User;
      user._id = self._id;
      user.name = self.name;
      user.isAdmin = self.isAdmin;
      user.birthday = self.birthday;
      user.items = self.items;
    },
    reset(): void {
      this.copyUser(this.user);
    },

    async upsert() {
      try {
        const res = await axios.put(`/User/${this.user._id}`, this.user);
        if (res.status === 200) {
          this.$bvModal.msgBoxOk('成功');
        } else {
          this.$bvModal.msgBoxOk('失敗', { headerBgVariant: 'danger' });
        }
        this.$emit('updated');
      } catch (err) {
        throw new Error('failed' + err);
      }
    },
  },
});
</script>
