<template>
  <div class="data-table-wrapper">
    <el-table
      v-loading="loading"
      :data="data"
      stripe
      border
      style="width: 100%"
      @selection-change="handleSelectionChange"
    >
      <el-table-column v-if="showSelection" type="selection" width="55" />
      <el-table-column v-if="showIndex" type="index" label="序号" width="60" fixed="left" />
      <template v-for="col in columns" :key="col.prop">
        <el-table-column
          v-if="!col.noRender"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :min-width="col.minWidth"
          :align="col.align || 'left'"
          :sortable="col.sortable"
          :fixed="col.fixed"
        >
          <template #default="scope">
            <slot :name="col.prop" :row="scope.row" :$index="scope.$index">
              {{ scope.row[col.prop] }}
            </slot>
          </template>
        </el-table-column>
      </template>
    </el-table>
    <el-pagination
      v-if="showPagination"
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      class="pagination"
      @size-change="handlePageChange"
      @current-change="handlePageChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

export interface TableColumn {
  prop: string
  label: string
  width?: number | string
  minWidth?: number | string
  align?: 'left' | 'center' | 'right'
  sortable?: boolean
  fixed?: 'left' | 'right' | boolean
  noRender?: boolean
}

interface Props {
  columns: TableColumn[]
  data: any[]
  loading?: boolean
  total?: number
  currentPage?: number
  pageSize?: number
  showPagination?: boolean
  showSelection?: boolean
  showIndex?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  total: 0,
  currentPage: 1,
  pageSize: 10,
  showPagination: true,
  showSelection: false,
  showIndex: true
})

const emit = defineEmits<{
  (e: 'page-change', page: number, size: number): void
  (e: 'selection-change', selection: any[]): void
}>()

const currentPage = ref(props.currentPage)
const pageSize = ref(props.pageSize)

watch(() => props.currentPage, (val) => {
  currentPage.value = val
})

watch(() => props.pageSize, (val) => {
  pageSize.value = val
})

function handlePageChange() {
  emit('page-change', currentPage.value, pageSize.value)
}

function handleSelectionChange(selection: any[]) {
  emit('selection-change', selection)
}
</script>

<style scoped lang="scss">
.data-table-wrapper {
  background: var(--card-bg);
  border-radius: 4px;
  padding: 16px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
