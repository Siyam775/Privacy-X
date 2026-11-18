import Dexie, { Table } from "dexie";
import {
  LocalMediaEntity,
  CachedConversationEntity,
  CachedMessageEntity,
  ChatMessage,
  PendingOutgoingMessageEntity,
  BackupHistoryEntity,
} from "@/types/index";

export class PrivacyXLocalDb extends Dexie {
  localMedia!: Table<LocalMediaEntity, string>;
  cachedConversations!: Table<CachedConversationEntity, string>;
  cachedMessages!: Table<CachedMessageEntity, string>;
  pendingOutgoing!: Table<PendingOutgoingMessageEntity, string>;
  backupHistory!: Table<BackupHistoryEntity, string>;
  mediaFiles!: Table<{ mediaId: string; blob: Blob; mimeType: string; fileName: string }, string>;

  constructor() {
    super("privacyx_local_storage");
    this.version(1).stores({
      localMedia: "mediaId, messageId, conversationId, createdAtMs",
      cachedConversations: "id, updatedAtMs",
      cachedMessages: "id, conversationId, createdAtMs",
      pendingOutgoing: "clientMessageId, conversationId, status, createdAtMs",
      backupHistory: "backupId, createdAtMs",
    });
    this.version(2).stores({
      localMedia: "mediaId, messageId, conversationId, createdAtMs",
      cachedConversations: "id, updatedAtMs",
      cachedMessages: "id, conversationId, createdAtMs",
      pendingOutgoing: "clientMessageId, conversationId, status, createdAtMs",
      backupHistory: "backupId, createdAtMs",
      mediaFiles: "mediaId",
    });
  }
}

export const localDb = new PrivacyXLocalDb();

export class LocalMediaRepository {
  async saveLocalMedia(entity: LocalMediaEntity): Promise<void> {
    await localDb.localMedia.put(entity);
  }

  async findLocalMedia(mediaId: string, messageId: string): Promise<LocalMediaEntity | undefined> {
    return localDb.localMedia.where("mediaId").equals(mediaId).or("messageId").equals(messageId).first();
  }

  async getAllLocalMedia(): Promise<LocalMediaEntity[]> {
    return localDb.localMedia.toArray();
  }

  async getMediaForConversation(conversationId: string): Promise<LocalMediaEntity[]> {
    return localDb.localMedia.where("conversationId").equals(conversationId).toArray();
  }

  async linkMessageId(mediaId: string, messageId: string): Promise<void> {
    const entity = await localDb.localMedia.where("mediaId").equals(mediaId).first();
    if (entity) {
      entity.messageId = messageId;
      await localDb.localMedia.put(entity);
    }
  }

  async markExpiredCloudMedia(nowMs: number = Date.now()): Promise<void> {
    const expired = await localDb.localMedia.where("expiresAtMs").belowOrEqual(nowMs).toArray();
    await Promise.all(
      expired.map((e) => localDb.localMedia.update(e.mediaId, { cloudDeleted: true }))
    );
  }

  async cacheConversations(conversations: CachedConversationEntity[]): Promise<void> {
    await localDb.cachedConversations.bulkPut(conversations);
  }

  async getCachedConversations(): Promise<CachedConversationEntity[]> {
    return localDb.cachedConversations.toArray();
  }

  async cacheMessages(messages: CachedMessageEntity[]): Promise<void> {
    await localDb.cachedMessages.bulkPut(messages);
  }

  async cacheChatMessages(messages: ChatMessage[]): Promise<void> {
    if (!messages.length) return;
    await localDb.cachedMessages.bulkPut(messages.map((message) => ({
      ...message,
      createdAtMs: message.createdAt?.getTime() || Date.now(),
    }) as unknown as CachedMessageEntity));
  }

  async getCachedChatMessages(conversationId: string): Promise<ChatMessage[]> {
    const stored = await localDb.cachedMessages.where("conversationId").equals(conversationId).sortBy("createdAtMs");
    return stored as unknown as ChatMessage[];
  }

  async saveMediaFile(mediaId: string, blob: Blob, mimeType: string, fileName: string): Promise<void> {
    await localDb.mediaFiles.put({ mediaId, blob, mimeType, fileName });
  }

  async getMediaFile(mediaId: string): Promise<Blob | undefined> {
    return (await localDb.mediaFiles.get(mediaId))?.blob;
  }

  async getCachedMessages(conversationId: string): Promise<CachedMessageEntity[]> {
    return localDb.cachedMessages.where("conversationId").equals(conversationId).toArray();
  }

  async enqueuePendingOutgoing(item: PendingOutgoingMessageEntity): Promise<void> {
    await localDb.pendingOutgoing.put(item);
  }

  async getPendingOutgoing(): Promise<PendingOutgoingMessageEntity[]> {
    return localDb.pendingOutgoing.where("status").notEqual("sent").toArray();
  }

  async deletePendingOutgoing(clientMessageId: string): Promise<void> {
    await localDb.pendingOutgoing.delete(clientMessageId);
  }

  async addBackupHistory(item: BackupHistoryEntity): Promise<void> {
    await localDb.backupHistory.put(item);
  }

  async getBackupHistory(): Promise<BackupHistoryEntity[]> {
    return localDb.backupHistory.toArray();
  }

  async getStorageStats(): Promise<{
    localImagesCount: number;
    localVoiceCount: number;
    localDocumentsCount: number;
    pendingOfflineMessagesCount: number;
  }> {
    const media = await this.getAllLocalMedia();
    const pending = await this.getPendingOutgoing();
    return {
      localImagesCount: media.filter((m) => m.mediaType === "image").length,
      localVoiceCount: media.filter((m) => m.mediaType === "voice").length,
      localDocumentsCount: media.filter((m) => m.mediaType === "document").length,
      pendingOfflineMessagesCount: pending.length,
    };
  }
}

export const localMediaRepository = new LocalMediaRepository();
