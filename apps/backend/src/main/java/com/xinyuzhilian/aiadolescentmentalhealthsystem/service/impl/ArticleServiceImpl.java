package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.*;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.content.vo.*;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.Result;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.*;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IArticleService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IUserArticleService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.ISysMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements IArticleService {

    private final ArticleInteractionMapper interactionMapper;
    private final ArticleCommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final AssessmentTemplateMapper assessmentMapper;
    private final UserArticleMapper userArticleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final IUserArticleService userArticleService;
    private final ISysMessageService sysMessageService;

    @Override
    public PageResult<Article> getArticleList(Integer page, Integer size, String type) {
        Page<Article> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getStatus, 1);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Article::getType, type);
        }
        wrapper.orderByDesc(Article::getCreateTime);
        Page<Article> result = this.page(pageParam, wrapper);
        
        // 设置 tagName 字段用于前端显示和筛选
        for (Article article : result.getRecords()) {
            article.setTagName(convertTypeToTagName(article.getType()));
        }
        
        return PageResult.build(result);
    }

    /**
     * 将 type 转换为 tagName
     * SCIENCE -> 科普
     * CASE -> 案例
     */
    private String convertTypeToTagName(String type) {
        if ("SCIENCE".equals(type)) {
            return "科普";
        } else if ("CASE".equals(type)) {
            return "案例";
        }
        return type;
    }

    @Override
    public PageResult<Article> getAdminArticles(Integer page, Integer size, String title, Long userId, Integer role) {
        Page<Article> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();

        if (title != null && !title.isEmpty()) {
            wrapper.like(Article::getTitle, title);
        }
        wrapper.orderByDesc(Article::getCreateTime);
        Page<Article> result = this.page(pageParam, wrapper);
        
        // 设置 tagName 字段用于前端显示和筛选
        for (Article article : result.getRecords()) {
            article.setTagName(convertTypeToTagName(article.getType()));
        }
        
        return PageResult.build(result);
    }

    /**
     * 管理员获取所有文章（包括管理员文章和用户文章）
     * 超级管理员(role=4)可以查看所有文章
     * 用户文章由超级管理员统一管理
     */
    @Override
    public PageResult<ArticleVO> getAllArticlesForAdmin(Integer page, Integer size, String title, Long userId, Integer role) {
        List<ArticleVO> allArticles = new ArrayList<>();

        // 1. 获取管理员文章
        Page<Article> adminPageParam = new Page<>(page, size);
        LambdaQueryWrapper<Article> adminWrapper = new LambdaQueryWrapper<>();

        if (title != null && !title.isEmpty()) {
            adminWrapper.like(Article::getTitle, title);
        }
        adminWrapper.orderByDesc(Article::getCreateTime);

        Page<Article> adminResult = this.page(adminPageParam, adminWrapper);

        for (Article article : adminResult.getRecords()) {
            ArticleVO vo = convertToArticleVO(article, "admin");
            // 管理员文章只有超级管理员可编辑，或者自己是作者
            vo.setEditable(role != null && (role == 4 || article.getAuthorId().equals(userId)));
            allArticles.add(vo);
        }

        // 2. 如果是超级管理员，获取用户文章
        if (role != null && role == 4) {
            Page<UserArticle> userPageParam = new Page<>(page, size);
            LambdaQueryWrapper<UserArticle> userWrapper = new LambdaQueryWrapper<>();

            if (title != null && !title.isEmpty()) {
                userWrapper.like(UserArticle::getTitle, title);
            }
            userWrapper.orderByDesc(UserArticle::getCreateTime);

            Page<UserArticle> userResult = userArticleMapper.selectPage(userPageParam, userWrapper);

            for (UserArticle article : userResult.getRecords()) {
                ArticleVO vo = convertToUserArticleVO(article);
                // 用户文章只有超级管理员可编辑
                vo.setEditable(false); // 超级管理员只能下架，不能编辑用户文章
                allArticles.add(vo);
            }

            // 合并后按时间排序
            allArticles.sort((a, b) -> {
                if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
                if (a.getCreateTime() == null) return 1;
                if (b.getCreateTime() == null) return -1;
                return b.getCreateTime().compareTo(a.getCreateTime());
            });
        }

        // 分页
        int total = allArticles.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);
        List<ArticleVO> pagedList = start < total ? allArticles.subList(start, end) : new ArrayList<>();

        PageResult<ArticleVO> result = new PageResult<>();
        result.setRecords(pagedList);
        result.setTotal((long) total);
        result.setCurrent((long) page);
        result.setSize((long) size);
        result.setPages((long) ((total + size - 1) / size));

        return result;
    }

    /**
     * 将管理员Article转换为ArticleVO
     */
    private ArticleVO convertToArticleVO(Article article, String source) {
        ArticleVO vo = new ArticleVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setCoverUrl(article.getCoverUrl());
        vo.setType(article.getType());
        vo.setTagName(convertTypeToTagName(article.getType()));
        vo.setStatus(article.getStatus());
        vo.setCreateTime(article.getCreateTime());
        vo.setSource(source);

        if (article.getAuthorId() != null) {
            User author = userMapper.selectById(article.getAuthorId());
            if (author != null) {
                vo.setAuthorId(author.getId());
                vo.setAuthorNickname(author.getNickname());
                vo.setAuthorAvatar(author.getHeadPath());
                vo.setAuthorRole(author.getRole());
            }
        }

        return vo;
    }

    /**
     * 将用户文章UserArticle转换为ArticleVO
     */
    private ArticleVO convertToUserArticleVO(UserArticle article) {
        ArticleVO vo = new ArticleVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setCoverUrl(article.getCoverUrl());
        vo.setTagName(article.getTagId() != null ? 
                Optional.ofNullable(articleTagMapper.selectById(article.getTagId())).map(ArticleTag::getName).orElse(null) : null);
        vo.setStatus(article.getStatus());
        vo.setCreateTime(article.getCreateTime());
        vo.setSource("user");

        // 获取标签信息
        if (article.getTagId() != null) {
            ArticleTag tag = articleTagMapper.selectById(article.getTagId());
            if (tag != null) {
                vo.setTagName(tag.getName());
            }
        }

        if (article.getUserId() != null) {
            User author = userMapper.selectById(article.getUserId());
            if (author != null) {
                vo.setAuthorId(author.getId());
                vo.setAuthorNickname(author.getNickname());
                vo.setAuthorAvatar(author.getHeadPath());
                vo.setAuthorRole(author.getRole());
            }
        }

        return vo;
    }

    @Override
    public Result<String> saveArticle(Article article, Long userId, Integer role) {
        if (article.getId() == null) {
            // 新增文章
            article.setCreateTime(LocalDateTime.now());
            article.setAuthorId(userId);
            article.setLike_count(0);
            article.setDislike_count(0);
            article.setCollection_count(0);
            article.setComment_count(0);
            article.setView_count(0);
            this.save(article);
            return Result.success("保存成功");
        } else {
            // 更新文章，需要检查权限
            Article existing = this.getById(article.getId());
            if (existing == null) return Result.error("文章不存在");
            
            // 如果不是超级管理员（角色4），且不是文章作者，则无权修改
            if (role != null && role != 4 && !existing.getAuthorId().equals(userId)) {
                return Result.error("无权修改他人发布的文章");
            }
            
            this.updateById(article);
            return Result.success("更新成功");
        }
    }

    @Override
    public Result<String> deleteArticle(Long id, Long userId, Integer role) {
        Article existing = this.getById(id);
        if (existing == null) return Result.error("文章不存在");
        
        // 如果不是超级管理员（角色4），且不是文章作者，则无权删除
        if (role != null && role != 4 && !existing.getAuthorId().equals(userId)) {
            return Result.error("无权删除他人发布的文章");
        }
        
        this.removeById(id);
        return Result.success("删除成功");
    }

    @Override
    public Result<ArticleDetailVO> getArticleDetail(Long id, Long userId) {
        Article article = this.getById(id);
        if (article == null) return Result.error("文章不存在");

        // 更新阅读数
        article.setView_count((article.getView_count() == null ? 0 : article.getView_count()) + 1);
        
        // 彻底同步所有统计数据（防止数据不一致）
        Integer likeCount = interactionMapper.selectCount(new LambdaQueryWrapper<ArticleInteraction>().eq(ArticleInteraction::getArticleId, id).eq(ArticleInteraction::getType, 1)).intValue();
        Integer dislikeCount = interactionMapper.selectCount(new LambdaQueryWrapper<ArticleInteraction>().eq(ArticleInteraction::getArticleId, id).eq(ArticleInteraction::getType, 2)).intValue();
        Integer collectionCount = interactionMapper.selectCount(new LambdaQueryWrapper<ArticleInteraction>().eq(ArticleInteraction::getArticleId, id).eq(ArticleInteraction::getType, 3)).intValue();
        Integer commentCount = commentMapper.selectCount(new LambdaQueryWrapper<ArticleComment>().eq(ArticleComment::getArticleId, id)).intValue();
        
        article.setLike_count(likeCount);
        article.setDislike_count(dislikeCount);
        article.setCollection_count(collectionCount);
        article.setComment_count(commentCount);
        
        this.updateById(article);

        ArticleDetailVO vo = new ArticleDetailVO();
        vo.setArticle(article);

        // 获取作者信息
        if (article.getAuthorId() != null) {
            User author = userMapper.selectById(article.getAuthorId());
            if (author != null) {
                vo.setAuthorName(author.getNickname());
                vo.setAuthorAvatar(author.getHeadPath());
                vo.setAuthorRole(author.getRole());
            }
        }

        if (userId != null) {
            vo.setLiked(interactionMapper.selectCount(new LambdaQueryWrapper<ArticleInteraction>()
                    .eq(ArticleInteraction::getArticleId, id).eq(ArticleInteraction::getUserId, userId).eq(ArticleInteraction::getType, 1)) > 0);
            vo.setDisliked(interactionMapper.selectCount(new LambdaQueryWrapper<ArticleInteraction>()
                    .eq(ArticleInteraction::getArticleId, id).eq(ArticleInteraction::getUserId, userId).eq(ArticleInteraction::getType, 2)) > 0);
            vo.setCollected(interactionMapper.selectCount(new LambdaQueryWrapper<ArticleInteraction>()
                    .eq(ArticleInteraction::getArticleId, id).eq(ArticleInteraction::getUserId, userId).eq(ArticleInteraction::getType, 3)) > 0);
        }

        // 随机推荐
        vo.setRecommendedArticles(baseMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1).ne(Article::getId, id).last("ORDER BY RAND() LIMIT 3")));
        vo.setRecommendedCourses(courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, 1).last("ORDER BY RAND() LIMIT 3")));
        vo.setRecommendedAssessments(assessmentMapper.selectList(new LambdaQueryWrapper<AssessmentTemplate>()
                .eq(AssessmentTemplate::getStatus, 1).last("ORDER BY RAND() LIMIT 3")));

        return Result.success(vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> interact(Long articleId, Integer type, Long userId) {
        Article article = this.getById(articleId);
        if (article == null) return Result.error("文章不存在");

        LambdaQueryWrapper<ArticleInteraction> wrapper = new LambdaQueryWrapper<ArticleInteraction>()
                .eq(ArticleInteraction::getArticleId, articleId)
                .eq(ArticleInteraction::getUserId, userId)
                .eq(ArticleInteraction::getType, type);

        ArticleInteraction existing = interactionMapper.selectOne(wrapper);
        if (existing != null) {
            interactionMapper.deleteById(existing.getId());
            updateArticleCount(articleId, type, -1);
            return Result.success("取消成功");
        } else {
            // 如果是点赞，要先取消踩；如果是踩，要先取消点赞
            if (type == 1) {
                cancelInteraction(articleId, userId, 2);
            } else if (type == 2) {
                cancelInteraction(articleId, userId, 1);
            }

            ArticleInteraction interaction = new ArticleInteraction();
            interaction.setArticleId(articleId);
            interaction.setUserId(userId);
            interaction.setType(type);
            interaction.setCreateTime(LocalDateTime.now());
            interactionMapper.insert(interaction);
            updateArticleCount(articleId, type, 1);

            // 点赞时发送通知给文章作者
            if (type == 1 && article.getAuthorId() != null && !article.getAuthorId().equals(userId)) {
                User liker = userMapper.selectById(userId);
                if (liker != null) {
                    sysMessageService.sendArticleLikeNotification(
                        article.getAuthorId(), userId, liker.getNickname(), liker.getHeadPath(),
                        articleId, article.getTitle()
                    );
                }
            }

            return Result.success("操作成功");
        }
    }

    private void cancelInteraction(Long articleId, Long userId, Integer type) {
        LambdaQueryWrapper<ArticleInteraction> wrapper = new LambdaQueryWrapper<ArticleInteraction>()
                .eq(ArticleInteraction::getArticleId, articleId)
                .eq(ArticleInteraction::getUserId, userId)
                .eq(ArticleInteraction::getType, type);
        if (interactionMapper.delete(wrapper) > 0) {
            updateArticleCount(articleId, type, -1);
        }
    }

    private void updateArticleCount(Long articleId, Integer type, int delta) {
        if (type == 1) baseMapper.updateLikeCount(articleId, delta);
        else if (type == 2) baseMapper.updateDislikeCount(articleId, delta);
        else if (type == 3) baseMapper.updateCollectionCount(articleId, delta);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> addComment(ArticleComment comment, Long userId) {
        Article article = this.getById(comment.getArticleId());
        if (article == null) return Result.error("文章不存在");

        User commenter = userMapper.selectById(userId);

        comment.setUserId(userId);
        comment.setLikeCount(0);
        comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);

        baseMapper.updateCommentCount(comment.getArticleId(), 1);

        // 发送通知给文章作者
        if (article.getAuthorId() != null && !article.getAuthorId().equals(userId)) {
            sysMessageService.sendCommentReplyNotification(
                article.getAuthorId(), userId, commenter != null ? commenter.getNickname() : "某用户",
                commenter != null ? commenter.getHeadPath() : null,
                comment.getId(), article.getId(), article.getTitle(),
                null, null
            );
        }

        // 如果是回复，额外通知被回复的人
        if (comment.getParentId() != null && comment.getParentId() != 0 && comment.getReplyToUserId() != null
                && !comment.getReplyToUserId().equals(userId) && !comment.getReplyToUserId().equals(article.getAuthorId())) {
            sysMessageService.sendCommentReplyNotification(
                comment.getReplyToUserId(), userId, commenter != null ? commenter.getNickname() : "某用户",
                commenter != null ? commenter.getHeadPath() : null,
                comment.getId(), article.getId(), article.getTitle(),
                comment.getReplyToUserId(), null
            );
        }

        return Result.success("评论成功");
    }

    @Override
    public Result<List<ArticleCommentVO>> getComments(Long articleId, Long userId) {
        List<ArticleComment> allComments = commentMapper.selectList(new LambdaQueryWrapper<ArticleComment>()
                .eq(ArticleComment::getArticleId, articleId)
                .orderByDesc(ArticleComment::getLikeCount, ArticleComment::getCreateTime));
        
        if (allComments.isEmpty()) return Result.success(Collections.emptyList());

        List<Long> userIds = allComments.stream().map(ArticleComment::getUserId).collect(Collectors.toList());
        userIds.addAll(allComments.stream().filter(c -> c.getReplyToUserId() != null).map(ArticleComment::getReplyToUserId).collect(Collectors.toList()));
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Long> likedCommentIds = userId == null ? Collections.emptyList() : 
            commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLike>().eq(CommentLike::getUserId, userId))
            .stream().map(CommentLike::getCommentId).collect(Collectors.toList());

        List<ArticleCommentVO> vos = allComments.stream().map(c -> {
            ArticleCommentVO vo = new ArticleCommentVO();
            vo.setId(c.getId());
            vo.setArticleId(c.getArticleId());
            vo.setUserId(c.getUserId());
            User user = userMap.get(c.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setHeadPath(user.getHeadPath());
            }
            vo.setParentId(c.getParentId());
            vo.setReplyToUserId(c.getReplyToUserId());
            if (c.getReplyToUserId() != null) {
                User replyUser = userMap.get(c.getReplyToUserId());
                if (replyUser != null) vo.setReplyToNickname(replyUser.getNickname());
            }
            vo.setContent(c.getContent());
            vo.setLikeCount(c.getLikeCount());
            vo.setLiked(likedCommentIds.contains(c.getId()));
            vo.setCreateTime(c.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        // 构建树形结构
        List<ArticleCommentVO> rootComments = vos.stream().filter(v -> v.getParentId() == 0).collect(Collectors.toList());
        for (ArticleCommentVO root : rootComments) {
            root.setReplies(vos.stream().filter(v -> v.getParentId().equals(root.getId())).collect(Collectors.toList()));
        }

        return Result.success(rootComments);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> likeComment(Long commentId, Long userId) {
        LambdaQueryWrapper<CommentLike> wrapper = new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getCommentId, commentId)
                .eq(CommentLike::getUserId, userId);

        CommentLike existing = commentLikeMapper.selectOne(wrapper);
        ArticleComment comment = commentMapper.selectById(commentId);
        if (comment == null) return Result.error("评论不存在");

        if (existing != null) {
            commentLikeMapper.deleteById(existing.getId());
            comment.setLikeCount(comment.getLikeCount() - 1);
        } else {
            CommentLike like = new CommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            like.setCreateTime(LocalDateTime.now());
            commentLikeMapper.insert(like);
            comment.setLikeCount(comment.getLikeCount() + 1);

            // 发送通知给评论作者
            if (comment.getUserId() != null && !comment.getUserId().equals(userId)) {
                User liker = userMapper.selectById(userId);
                Article article = this.getById(comment.getArticleId());
                if (liker != null && article != null) {
                    sysMessageService.sendCommentLikeNotification(
                        comment.getUserId(), userId, liker.getNickname(), liker.getHeadPath(),
                        commentId, article.getId(), article.getTitle()
                    );
                }
            }
        }
        commentMapper.updateById(comment);
        return Result.success("操作成功");
    }
}
