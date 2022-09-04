<template>
  <div>
    <b-card title="掃描書籍條碼 🤟">
      <div id="scan-area"></div>
    </b-card>
    <b-card title="輸入書籍條碼借書 🤟">
      <b-form @submit.prevent>
        <b-form-group label="帳號:" label-for="account" label-cols="3">
          <b-input
            id="account"
            v-model="code"
            :state="code.length !== 0"
            aria-describedby="account-feedback"
          ></b-input>
          <b-form-invalid-feedback>條碼不能是空的</b-form-invalid-feedback>
        </b-form-group>
        <b-row>
          <b-col offset-md="3">
            <b-button
              variant="gradient-primary"
              type="submit"
              class="mr-1"
              @click="returnBook"
            >
              確認歸還
            </b-button>
          </b-col>
        </b-row>
      </b-form>
    </b-card>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
const Quagga = require('quagga');
import axios from 'axios';
import { mapState } from 'vuex';
export default Vue.extend({
  data() {
    return {
      code: '',
    };
  },
  computed: {
    ...mapState('user', ['userInfo']),
  },
  mounted() {
    let me = this;
    Quagga.init(
      {
        inputStream: {
          name: 'Live',
          type: 'LiveStream',
          constraints: {
            width: 640,
            height: 480,
            facingMode: 'environment',
            deviceId: '7832475934759384534',
          },
          area: {
            // defines rectangle of the detection/localization area
            top: '0%', // top offset
            right: '0%', // right offset
            left: '0%', // left offset
            bottom: '0%', // bottom offset
          },
          singleChannel: false, // true: only the red color-channel is read
          target: document.querySelector('#scan-area'), // Or '#yourElement' (optional)
        },
        locate: true,
        decoder: {
          readers: ['code_128_reader'],
          debug: {
            drawBoundingBox: true,
            showFrequency: false,
            drawScanline: true,
            showPattern: false,
          },
        },
      },
      function (err: any) {
        if (err) {
          console.log(err);
          return;
        }
        console.log('Initialization finished. Ready to start');
        Quagga.start();
      },
    );
    Quagga.onDetected(me.onDetected);
  },
  beforeDestroy() {
    Quagga.stop();
  },
  methods: {
    onDetected(msg: any) {
      console.log(msg);
      if (this.code !== msg.code) {
        this.code = msg.code;
        Quagga.stop();
        //let ret = await this.$bvModal.msgBoxOk(`借出${this.code}`);
      }
    },
    async returnBook() {
      try {
        let ret = await axios.post('/ReturnBook', {
          bookId: this.code,
        });
        if (ret.status === 200) {
          this.$bvModal.msgBoxOk('成功歸還');
        }
      } catch (err) {
        this.$bvModal.msgBoxOk(`歸還失敗 ${err}`);
      }
    },
  },
});
</script>
<style></style>
