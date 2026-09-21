<script setup lang="ts">
/**
 * 咨询订单列表。
 *
 * 取数与字段映射全部来自 `src/lib/appointments.ts`（菜单 2 也将复用该处）。
 * 本页以**订单/支付维度**展示：订单号、金额、支付状态、下单与预约时间；分页与四态走共用件。
 *
 * 关于支付状态：
 * - 后端 `psychologist_appointment.pay_status` 取值为 0 / 1 / 2，返回体字段名是 camelCase 的 `payStatus`；
 * - 本版是虚拟支付，用户侧正常情况下不会出现 0；但界面**必须**能正确显示三种取值，
 *   因此这里不做任何「一律已支付」的假设，逐条按实际值渲染；
 * - 后端 `/psychologist/appointment/my` 只支持按**预约状态**（status）过滤，不支持按支付状态过滤，
 *   所以本页不提供支付状态筛选——不做一个只过滤当前页、却在分页下会误导人的假筛选。
 *
 * 非 tab 页，按约定用 onLoad 取数。
 */

import { onLoad, onUnload } from "@dcloudio/uni-app";
import MCard from "@/components/MCard.vue";
import MListState from "@/components/MListState.vue";
import MNavBar from "@/components/MNavBar.vue";
import {
  appointmentStatusLabel,
  fetchMyAppointments,
  PAY_STATUS_LABELS,
  payStatusLabel,
  type AppointmentOrder,
} from "@/lib/appointments";
import { usePagedList } from "@/lib/use-paged-list";
import { ensureAuthenticated } from "@/router/guard";
import { ME_ORDERS_PAGE } from "@/router/routes";

const { items, loading, loadingMore, error, total, hasMore, isEmpty, loadFirstPage, loadMore, dispose } =
  usePagedList<AppointmentOrder>((page, size) => fetchMyAppointments({ page, size }), {
    size: 10,
    fallbackError: "订单加载失败",
  });

onLoad(() => {
  if (!ensureAuthenticated(ME_ORDERS_PAGE)) return;
  void loadFirstPage();
});

onUnload(() => {
  dispose();
});

function formatFee(fee: number): string {
  return `¥${fee.toFixed(2)}`;
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="咨询订单" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="说明" muted>
          <text class="m-text-primary">订单按下单时间倒序排列，可下拉到底部继续加载。</text>
          <text class="m-text-secondary">
            支付状态共三种：{{ PAY_STATUS_LABELS[0] }} / {{ PAY_STATUS_LABELS[1] }} /
            {{ PAY_STATUS_LABELS[2] }}；本页按每笔订单的实际状态显示。
          </text>
        </MCard>

        <MCard title="订单列表">
          <MListState
            :loading="loading"
            :error="error"
            :empty="isEmpty"
            empty-text="还没有咨询订单"
            :loading-more="loadingMore"
            :has-more="hasMore"
            :total="total"
            :loaded-count="items.length"
            @retry="loadFirstPage"
            @load-more="loadMore"
          >
            <view class="m-list">
              <view
                v-for="(item, index) in items"
                :key="item.id"
                class="m-item"
                :class="{ 'm-item--last': index === items.length - 1 }"
              >
                <view class="m-item__head">
                  <text class="m-item__title">{{ item.psychologistName }}</text>
                  <text class="m-item__tag">{{ payStatusLabel(item) }}</text>
                </view>
                <view class="m-item__meta">
                  <text>订单号</text>
                  <text>{{ item.orderNo || "无" }}</text>
                </view>
                <view class="m-item__meta">
                  <text>金额</text>
                  <text>{{ formatFee(item.fee) }}</text>
                </view>
                <view class="m-item__meta">
                  <text>预约时间</text>
                  <text>{{ item.appointmentTime || "待定" }}</text>
                </view>
                <view class="m-item__meta">
                  <text>预约状态</text>
                  <text>{{ appointmentStatusLabel(item) }}</text>
                </view>
              </view>
            </view>
          </MListState>
        </MCard>
      </view>
    </view>
  </view>
</template>
