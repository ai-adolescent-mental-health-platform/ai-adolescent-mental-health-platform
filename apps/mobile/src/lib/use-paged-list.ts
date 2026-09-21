import { computed, ref, type Ref } from "vue";

/**
 * 分页列表状态（所有列表页共用）。
 *
 * 为什么抽成组合式函数：手机端有多个页面、多类列表，若每个页面各写一套
 * loading / error / 空态 / 加载更多 的逻辑，「分页行为必须正确」就变成了多份各自为政的实现，
 * 改一处漏一处。这里把分页语义收成一处，页面只负责渲染条目。
 *
 * ---------------------------------------------------------------------------
 * 请求代际校验（为什么需要它）
 * ---------------------------------------------------------------------------
 * 移动网络的响应顺序不保证与请求顺序一致：先发的请求可能后到。典型场景是
 * **切换数据源**（本工作区实测：预约记录页从「全部」切到「已完成」，前者被 mock 延迟 4s、
 * 后者 200ms 返回）——旧请求的响应会晚于新请求返回，如果直接写入状态，
 * 就会出现「筛选显示已完成，列表里却是全部」这种自相矛盾的界面。
 *
 * 机制：每次「重新加载（换数据源）」都递增代际号，请求发出时记录当时的代际；
 * 响应返回后代际不匹配就直接丢弃，**不写入任何状态**。因此无论响应以什么顺序到达，
 * 只有最新代际的响应能落地。
 *
 * 重要：**它不替代取消链路，只是客户端侧的兜底**。adapter 目前丢弃了 `uni.request` 的
 * `RequestTask` 句柄、`abort()` 从未被调用（issue #32 遗留），因此旧请求仍会真实占用网络与
 * 服务端资源，只是其结果被丢弃。等取消链路修好后，这里仍建议保留——
 * 两道防线解决的不是同一个问题（一个省流量与资源，一个保证状态一致）。
 *
 * 分页语义（与后端 PageResult 对齐）：
 * - `current` 是当前已加载到的页码，`pages` 是总页数（后端可能不返回，故做兜底判断）
 * - 首屏失败会清空列表并把 current 复位，避免「失败后残留旧数据 + 还能加载更多」的错位
 * - 加载更多在 hasMore 为假、或已有请求在飞时直接返回，避免重复追加同一页
 */

/** 与后端 `PageResult` 同形（api-client 的 mapPage 返回值）。 */
export type PageResultLike<T> = {
  total: number;
  records: T[];
  current: number;
  size: number;
  pages?: number;
};

export type PagedList<T> = {
  items: Ref<T[]>;
  loading: Ref<boolean>;
  loadingMore: Ref<boolean>;
  error: Ref<string>;
  total: Ref<number>;
  hasMore: Ref<boolean>;
  isEmpty: Ref<boolean>;
  /** 重新加载首页。换数据源（切筛选、切 tab）时必须走这里：它会递增代际、作废在飞请求。 */
  loadFirstPage: () => Promise<void>;
  loadMore: () => Promise<void>;
  /** 组件卸载时调用：作废在飞请求并清掉加载态，避免响应回来后写已卸载页面的状态。 */
  dispose: () => void;
};

export function usePagedList<T>(
  fetcher: (page: number, size: number) => Promise<PageResultLike<T>>,
  options: { size?: number; fallbackError?: string } = {},
): PagedList<T> {
  const size = options.size ?? 10;
  const fallbackError = options.fallbackError ?? "加载失败，请重试";

  const items = ref<T[]>([]) as Ref<T[]>;
  const loading = ref(false);
  const loadingMore = ref(false);
  const error = ref("");
  const total = ref(0);
  const current = ref(0);
  const pages = ref(0);

  /**
   * 当前代际。只有等于该值的响应才允许写入状态。
   * 用普通变量而不是 ref：它不参与渲染，只是请求与响应之间的对账凭据。
   */
  let generation = 0;

  /** 有总页数时按页数判断；后端没给 pages 时退化为按已加载条数与总数比较。 */
  const hasMore = computed(() => {
    if (current.value <= 0) return false;
    if (pages.value > 0) return current.value < pages.value;
    return items.value.length < total.value;
  });

  const isEmpty = computed(() => !loading.value && !error.value && items.value.length === 0);

  async function fetchPage(page: number, append: boolean): Promise<void> {
    // 首屏 / 重新加载 = 换数据源，递增代际使此前所有在飞请求失效；
    // 加载更多属于当前数据源，沿用当前代际。
    const token = append ? generation : (generation += 1);

    if (append) {
      loadingMore.value = true;
    } else {
      loading.value = true;
    }
    error.value = "";

    try {
      const result = await fetcher(page, size);

      // 代际不匹配：这是被作废的旧请求，直接丢弃，不写任何状态。
      if (token !== generation) {
        // 追加请求被作废时要把按钮的 loading 收掉，否则「加载更多」会一直转。
        // 首屏请求被作废时不动 loading：要么有更新的请求在飞会收尾，要么已被 dispose 清掉。
        if (append) loadingMore.value = false;
        return;
      }

      const records = Array.isArray(result?.records) ? result.records : [];
      total.value = typeof result?.total === "number" ? result.total : records.length;
      current.value = typeof result?.current === "number" && result.current > 0 ? result.current : page;
      pages.value = typeof result?.pages === "number" ? result.pages : 0;
      items.value = append ? [...items.value, ...records] : records;
    } catch (err) {
      if (token !== generation) {
        if (append) loadingMore.value = false;
        return;
      }
      error.value = err instanceof Error && err.message ? err.message : fallbackError;
      if (!append) {
        // 首屏失败：清空并复位，避免残留旧数据与错误的分页游标。
        items.value = [];
        total.value = 0;
        current.value = 0;
        pages.value = 0;
      }
    } finally {
      if (token === generation) {
        loading.value = false;
        loadingMore.value = false;
      }
    }
  }

  const loadFirstPage = (): Promise<void> => fetchPage(1, false);

  const loadMore = async (): Promise<void> => {
    if (!hasMore.value || loading.value || loadingMore.value) return;
    await fetchPage(current.value + 1, true);
  };

  function dispose(): void {
    // 递增代际：此后返回的响应全部作废。
    generation += 1;
    loading.value = false;
    loadingMore.value = false;
  }

  return {
    items,
    loading,
    loadingMore,
    error,
    total,
    hasMore,
    isEmpty,
    loadFirstPage,
    loadMore,
    dispose,
  };
}
