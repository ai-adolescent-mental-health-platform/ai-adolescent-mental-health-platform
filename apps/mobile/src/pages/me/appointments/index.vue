<script setup lang="ts">
/**
 * 预约记录。
 *
 * 取数与字段映射全部来自 `src/lib/appointments.ts`（菜单 2 也将复用该处）。
 * 本页只负责：状态筛选、渲染条目、分页四态。
 *
 * 状态筛选是 #35 未覆盖的列表形态（数据源会变），因此走「同一套共用件 + 切换筛选后重新加载首页」：
 * 旧数据源的在飞请求由 `use-paged-list` 的代际校验丢弃，不会覆盖新筛选的结果。
 * 非 tab 页，按约定用 onLoad 取数。
 */

import { onLoad, onUnload } from "@dcloudio/uni-app";
import { ref } from "vue";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MListState from "@/components/MListState.vue";
import MNavBar from "@/components/MNavBar.vue";
import {
  APPOINTMENT_STATUS_FILTERS,
  appointmentStatusLabel,
  fetchMyAppointments,
  payStatusLabel,
  type AppointmentOrder,
} from "@/lib/appointments";
import { usePagedList } from "@/lib/use-paged-list";
import { ensureAuthenticated } from "@/router/guard";
import { ME_APPOINTMENTS_PAGE, ME_ORDERS_PAGE } from "@/router/routes";

const activeFilter = ref(0);
const loadingFilter = ref(false);

const { items, loading, loadingMore, error, total, hasMore, isEmpty, loadFirstPage, loadMore, dispose } =
  usePagedList<AppointmentOrder>(
    (page, size) => {
      const filter = APPOINTMENT_STATUS_FILTERS[activeFilter.value];
      return fetchMyAppointments({ page, size, status: filter?.value });
    },
    { size: 10, fallbackError: "预约记录加载失败" },
  );

onLoad(() => {
  if (!ensureAuthenticated(ME_APPOINTMENTS_PAGE)) return;
  void loadFirstPage();
});

/** 切换筛选 = 换数据源：重新加载首页，在飞请求由代际校验作废。 */
async function selectFilter(index: number): Promise<void> {
  if (index === activeFilter.value) return;
  activeFilter.value = index;
  loadingFilter.value = true;
  try {
    await loadFirstPage();
  } finally {
    loadingFilter.value = false;
  }
}

function goOrders(): void {
  uni.navigateTo({ url: ME_ORDERS_PAGE });
}

function formatFee(fee: number): string {
  return `¥${fee.toFixed(2)}`;
}

onUnload(() => {
  // 离页时作废在飞请求：避免响应回来后写入已卸载页面的状态。
  dispose();
});
</script>

<template>
  <view class="m-page">
    <MNavBar title="预约记录" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="按预约状态筛选">
          <view class="filter">
            <view
              v-for="(filter, index) in APPOINTMENT_STATUS_FILTERS"
              :key="filter.label"
              class="m-choice"
              :class="{ 'm-choice--active': activeFilter === index }"
              @tap="selectFilter(index)"
            >
              <text class="m-choice__label">{{ filter.label }}</text>
            </view>
          </view>
          <text class="m-text-secondary">
            筛选在服务端生效（请求带 status 参数），不是只过滤当前页。
          </text>
        </MCard>

        <MCard title="预约列表">
          <MListState
            :loading="loading || loadingFilter"
            :error="error"
            :empty="isEmpty"
            empty-text="该状态下没有预约记录"
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
                  <text class="m-item__tag">{{ appointmentStatusLabel(item) }}</text>
                </view>
                <view class="m-item__meta">
                  <text>{{ item.serviceTypeText }}</text>
                  <text>{{ item.appointmentTime || "时间待定" }}</text>
                </view>
                <view class="m-item__meta">
                  <text>{{ formatFee(item.fee) }}</text>
                  <text>{{ payStatusLabel(item) }}</text>
                </view>
              </view>
            </view>
          </MListState>
        </MCard>

        <view class="m-actions">
          <MButton variant="ghost" @click="goOrders">查看咨询订单（含订单号与支付状态）</MButton>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.filter {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.filter .m-choice {
  flex: none;
  padding: 0 24rpx;
}
</style>
