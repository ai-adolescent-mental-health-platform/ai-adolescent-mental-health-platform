import { computed, ref, type Ref } from "vue";

/**
 * 分页列表状态（供「我的消息与互动」这一批列表页共用）。
 *
 * 为什么抽成组合式函数：这一批有 7 个页面、5 类列表，若每个页面各写一套
 * loading / error / 空态 / 加载更多 的逻辑，「分页行为必须正确」就变成了 7 份各自为政的实现，
 * 改一处漏一处。这里把分页语义收成一处，页面只负责渲染条目。
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
  loadFirstPage: () => Promise<void>;
  loadMore: () => Promise<void>;
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

  /** 有总页数时按页数判断；后端没给 pages 时退化为按已加载条数与总数比较。 */
  const hasMore = computed(() => {
    if (current.value <= 0) return false;
    if (pages.value > 0) return current.value < pages.value;
    return items.value.length < total.value;
  });

  const isEmpty = computed(() => !loading.value && !error.value && items.value.length === 0);

  async function fetchPage(page: number, append: boolean): Promise<void> {
    if (append) {
      loadingMore.value = true;
    } else {
      loading.value = true;
    }
    error.value = "";

    try {
      const result = await fetcher(page, size);
      const records = Array.isArray(result?.records) ? result.records : [];
      total.value = typeof result?.total === "number" ? result.total : records.length;
      current.value = typeof result?.current === "number" && result.current > 0 ? result.current : page;
      pages.value = typeof result?.pages === "number" ? result.pages : 0;
      items.value = append ? [...items.value, ...records] : records;
    } catch (err) {
      error.value = err instanceof Error && err.message ? err.message : fallbackError;
      if (!append) {
        // 首屏失败：清空并复位，避免残留旧数据与错误的分页游标。
        items.value = [];
        total.value = 0;
        current.value = 0;
        pages.value = 0;
      }
    } finally {
      loading.value = false;
      loadingMore.value = false;
    }
  }

  const loadFirstPage = (): Promise<void> => fetchPage(1, false);

  const loadMore = async (): Promise<void> => {
    if (!hasMore.value || loading.value || loadingMore.value) return;
    await fetchPage(current.value + 1, true);
  };

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
  };
}
